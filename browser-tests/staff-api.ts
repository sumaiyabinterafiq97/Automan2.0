import { expect, type APIRequestContext } from '@playwright/test';

type StaffLogin = { email: string; password: string; name?: string };
type AuthHeaders = { Authorization: string };

const SEEDED_ADMIN = {
  email: 'admin@automan.com',
  password: 'Automan!Ship26Tokyo',
};

/**
 * Logs in and returns a bearer header. Does not attach that header to later calls.
 */
export async function staffAuthHeaders(
  request: APIRequestContext,
  apiBase: string,
  admin: StaffLogin,
): Promise<AuthHeaders> {
  const login = await request.post(`${apiBase}/auth/login`, {
    data: { email: admin.email, password: admin.password },
  });
  expect(login.ok(), await login.text()).toBeTruthy();
  const body = (await login.json()) as { token: string };
  return { Authorization: `Bearer ${body.token}` };
}

/**
 * Opens an admin session for a populated or empty database.
 * First-admin setup is used only when no user exists. Otherwise the seeded admin
 * creates this test admin. Later calls on the same `request` send this session
 * unless the call sets its own Authorization header or `X-Skip-Auth: 1`.
 */
export async function openAdminSession(
  request: APIRequestContext,
  apiBase: string,
  admin: StaffLogin & { name: string },
): Promise<AuthHeaders> {
  const setup = await request.post(`${apiBase}/auth/setup`, {
    data: { email: admin.email, name: admin.name, password: admin.password },
  });
  if (setup.ok()) {
    const headers = await staffAuthHeaders(request, apiBase, admin);
    attachSession(request, headers);
    return headers;
  }

  const seededLogin = await request.post(`${apiBase}/auth/login`, { data: SEEDED_ADMIN });
  if (!seededLogin.ok()) {
    throw new Error(
      `Cannot open an admin session. setup=${setup.status()} ${await setup.text()} seeded=${seededLogin.status()} ${await seededLogin.text()}`,
    );
  }
  const seededBody = (await seededLogin.json()) as { token: string };
  const seededHeaders = { Authorization: `Bearer ${seededBody.token}` };

  const already = await request.post(`${apiBase}/auth/login`, {
    data: { email: admin.email, password: admin.password },
  });
  if (already.ok()) {
    const body = (await already.json()) as { token: string };
    const headers = { Authorization: `Bearer ${body.token}` };
    attachSession(request, headers);
    return headers;
  }

  const created = await request.post(`${apiBase}/users`, {
    headers: seededHeaders,
    data: { email: admin.email, name: admin.name, password: admin.password, role: 'ADMIN' },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const headers = await staffAuthHeaders(request, apiBase, admin);
  attachSession(request, headers);
  return headers;
}

function attachSession(request: APIRequestContext, headers: AuthHeaders) {
  const target = request as APIRequestContext & Record<string, unknown>;
  if (target.__automanSessionAttached) return;
  target.__automanSessionAttached = true;
  for (const method of ['get', 'post', 'put', 'delete', 'patch', 'fetch'] as const) {
    const original = (request[method] as Function).bind(request);
    target[method] = (url: string, options?: { headers?: Record<string, string> }) => {
      const callerHeaders = options?.headers || {};
      if (callerHeaders['X-Skip-Auth'] === '1') {
        const rest = { ...callerHeaders };
        delete rest['X-Skip-Auth'];
        delete rest.Authorization;
        return original(url, { ...options, headers: rest });
      }
      return original(url, {
        ...options,
        headers: { ...headers, ...callerHeaders },
      });
    };
  }
}
