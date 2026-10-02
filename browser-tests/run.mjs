import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..');
const logsDir = path.join(here, 'logs');
const h2Url = 'jdbc:h2:mem:automan_browser;MODE=MySQL;DB_CLOSE_DELAY=-1';

function listeningPids(port) {
  return new Promise((resolve) => {
    const child = spawn('lsof', ['-nP', `-iTCP:${port}`, '-sTCP:LISTEN', '-t'], {
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    let out = '';
    child.stdout.on('data', (chunk) => {
      out += chunk;
    });
    child.on('exit', () => {
      const pids = out.split(/\s+/).map((part) => Number(part)).filter((pid) => Number.isInteger(pid) && pid > 0);
      resolve([...new Set(pids)]);
    });
  });
}

function processCommand(pid) {
  return new Promise((resolve) => {
    const child = spawn('ps', ['-p', String(pid), '-o', 'command='], {
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    let out = '';
    child.stdout.on('data', (chunk) => {
      out += chunk;
    });
    child.on('exit', () => resolve(out.trim()));
  });
}

async function assertPortAvailable(port) {
  const pids = await listeningPids(port);
  if (pids.length === 0) return;
  const commands = await Promise.all(pids.map(async (pid) => `${pid} ${(await processCommand(pid)) || 'unknown'}`));
  throw new Error(`Port ${port} is already in use (${commands.join('; ')}). Refusing to start so this run cannot attach to MySQL.`);
}

async function waitUntilPortAvailable(port, timeoutMs) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if ((await listeningPids(port)).length === 0) return;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`Port ${port} stayed in use after stopping the disposable process.`);
}

function waitForHttp(url, timeoutMs, isReady, headers) {
  const started = Date.now();
  let lastError = 'no response';
  return new Promise((resolve, reject) => {
    const poll = async () => {
      try {
        const response = await fetch(url, { headers });
        if (response.ok && (!isReady || await isReady(response))) {
          resolve();
          return;
        }
        lastError = `HTTP ${response.status}`;
      } catch (error) {
        lastError = error instanceof Error ? error.message : String(error);
      }
      if (Date.now() - started > timeoutMs) {
        reject(new Error(`Timed out waiting for ${url}: ${lastError}`));
        return;
      }
      setTimeout(poll, 500);
    };
    poll();
  });
}

function waitForLog(file, timeoutMs, ready) {
  const started = Date.now();
  return new Promise((resolve, reject) => {
    const poll = () => {
      const text = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
      if (ready(text)) {
        resolve(text);
        return;
      }
      if (Date.now() - started > timeoutMs) {
        reject(new Error(`Timed out reading ${path.basename(file)}`));
        return;
      }
      setTimeout(poll, 500);
    };
    poll();
  });
}

function prepareFrontendStatic() {
  const destination = path.join(repoRoot, 'build/dist/js/developmentExecutable');
  const sources = [
    path.join(repoRoot, 'build/js/packages/automan-car-purchase/kotlin'),
    path.join(repoRoot, 'build/processedResources/js/main'),
  ];
  for (const source of sources) {
    if (!fs.existsSync(path.join(source, 'index.html'))) {
      throw new Error(`Frontend static output is missing at ${source}.`);
    }
  }
  fs.mkdirSync(destination, { recursive: true });
  for (const source of sources) {
    fs.cpSync(source, destination, { recursive: true });
  }
}

function startGradle(cwd, args, logName, env) {
  fs.mkdirSync(logsDir, { recursive: true });
  const log = fs.createWriteStream(path.join(logsDir, logName));
  const child = spawn('./gradlew', ['--no-daemon', ...args], {
    cwd,
    env,
    detached: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.pipe(log);
  child.stderr.pipe(log);
  return child;
}

function signalProcess(child, signal) {
  if (!child?.pid) return;
  try {
    process.kill(-child.pid, signal);
  } catch {
    try {
      child.kill(signal);
    } catch {
      // Already gone.
    }
  }
}

async function stopProcess(child, port) {
  if (child?.pid && !child.killed) {
    signalProcess(child, 'SIGTERM');
    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        signalProcess(child, 'SIGKILL');
        resolve();
      }, 8_000);
      child.once('exit', () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }
  if (port) await waitUntilPortAvailable(port, 20_000);
}

const backendEnv = {
  ...process.env,
  SPRING_PROFILES_ACTIVE: 'default',
  SPRING_DATASOURCE_URL: h2Url,
  SPRING_DATASOURCE_USERNAME: 'sa',
  SPRING_DATASOURCE_PASSWORD: '',
  SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.h2.Driver',
  SPRING_JPA_HIBERNATE_DDL_AUTO: 'update',
  SPRING_JPA_DATABASE_PLATFORM: 'org.hibernate.dialect.H2Dialect',
  SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: 'org.hibernate.dialect.H2Dialect',
  SPRING_FLYWAY_ENABLED: 'false',
  R2_ENABLED: 'false',
  RESEND_API_KEY: '',
  GMAIL_USERNAME: '',
  GMAIL_APP_PASSWORD: '',
  APP_MAIL_VALIDATE_MX: 'false',
  LOGGING_LEVEL_COM_ZAXXER_HIKARI: 'DEBUG',
};

const backendArgs = [
  'bootRun',
  `--args=--spring.datasource.url=${h2Url} --spring.datasource.username=sa --spring.datasource.password= --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.hibernate.ddl-auto=update --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect --spring.flyway.enabled=false --logging.level.com.zaxxer.hikari=DEBUG`,
];

function specFiles() {
  const requested = process.argv.slice(2).filter((arg) => !arg.startsWith('-'));
  if (requested.length > 0) return requested;
  return fs.readdirSync(path.join(here, 'tests'))
    .filter((name) => name.endsWith('.spec.ts'))
    .sort()
    .map((name) => path.join('tests', name));
}

async function startDisposableBackend() {
  await assertPortAvailable(8083);
  const backendLogPath = path.join(logsDir, 'backend.log');
  const child = startGradle(path.join(repoRoot, 'backend'), backendArgs, 'backend.log', backendEnv);
  const backendLog = await waitForLog(
    backendLogPath,
    180_000,
    (text) => text.includes(h2Url) && text.includes('Started BackendApplicationKt'),
  );
  if (/jdbc:mysql:\/\//.test(backendLog)) {
    throw new Error('Backend log contains a MySQL URL. Aborting before any browser work.');
  }
  await waitForHttp('http://127.0.0.1:8083/api/actuator/health', 30_000);
  return child;
}

function runPlaywright(spec) {
  return new Promise((resolve) => {
    const test = spawn('npx', ['playwright', 'test', spec], {
      cwd: here,
      stdio: 'inherit',
      env: process.env,
    });
    test.on('exit', (code) => resolve(code ?? 1));
  });
}

let backend;
let frontend;
let exitCode = 1;

try {
  await assertPortAvailable(8083);
  await assertPortAvailable(8081);

  prepareFrontendStatic();
  frontend = startGradle(repoRoot, ['jsBrowserDevelopmentRun'], 'frontend.log', process.env);
  await waitForHttp('http://[::1]:8081/login', 240_000, async (response) => {
    const body = await response.text();
    return body.includes('automan-car-purchase.js');
  }, { Accept: 'text/html' });
  await waitForHttp('http://[::1]:8081/automan-car-purchase.js', 240_000, async (response) => {
    const body = await response.text();
    return body.length > 0 && !body.trimStart().startsWith('<');
  });

  exitCode = 0;
  for (const spec of specFiles()) {
    console.log(`\n--- ${spec} (fresh in-memory database) ---`);
    backend = await startDisposableBackend();
    const code = await runPlaywright(spec);
    await stopProcess(backend, 8083);
    backend = undefined;
    if (code !== 0) exitCode = code;
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  for (const name of ['backend.log', 'frontend.log']) {
    const file = path.join(logsDir, name);
    if (!fs.existsSync(file)) continue;
    const text = fs.readFileSync(file, 'utf8');
    console.error(`\n--- ${name} (tail) ---\n${text.slice(-4000)}`);
  }
  exitCode = 1;
} finally {
  await stopProcess(frontend, 8081);
  await stopProcess(backend, 8083);
}

process.exit(exitCode);
