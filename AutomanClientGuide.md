Automan Client Run Guide (Linux, macOS, Windows)

Prerequisites
- Install and start Docker Desktop.
  - macOS: https://www.docker.com/products/docker-desktop/
  - Windows: https://www.docker.com/products/docker-desktop/ (enable WSL2)
  - Linux: Install Docker Engine + Docker Compose plugin from your distro docs
- Download the ZIP: automan-client-package-20250902.zip
- Extract it to a folder (e.g., Downloads/Desktop)

What’s included
- docker-compose.yml, Dockerfile, build-docker.sh, source code
- One command starts: MySQL, Backend API, Frontend, phpMyAdmin

Service URLs (after start)
- Frontend: http://localhost:8080
- Backend API: http://localhost:8083
- phpMyAdmin: http://localhost:8082
  - Username: automan_user
  - Password: automan_password

macOS
1) Open Terminal (Cmd + Space → Terminal)
2) cd to the extracted folder, e.g.:
   cd ~/Downloads/automan-client-package-20250902/automan-client-package-20250902
3) First run only: make script executable
   chmod +x build-docker.sh
4) Start:
   ./build-docker.sh
5) Open http://localhost:8080

Linux
1) Open Terminal
2) cd to the extracted folder, e.g.:
   cd ~/Downloads/automan-client-package-20250902/automan-client-package-20250902
3) First run only:
   chmod +x build-docker.sh
4) Start:
   ./build-docker.sh
5) Open http://localhost:8080

Windows (PowerShell or Git Bash)
Option A: PowerShell (no chmod needed)
1) Open PowerShell in the extracted folder
2) Run:
   .\build-docker.sh
3) Open http://localhost:8080

Option B: Git Bash (if PowerShell fails to run the script)
1) Right-click folder → “Git Bash Here”
2) Run:
   chmod +x build-docker.sh
   ./build-docker.sh
3) Open http://localhost:8080

Manage the system
- Stop: docker-compose down
- Logs: docker-compose logs -f
- Restart: docker-compose restart
- Rebuild: docker-compose up -d --build

Troubleshooting
- “This site can’t be reached” on 8080:
  - Ensure Docker Desktop is running
  - Check containers: docker ps
  - Frontend logs: docker-compose logs -f frontend
  - Port busy? Edit docker-compose.yml to map “8081:8080”, then:
    docker-compose down && docker-compose up -d --build
    Open http://localhost:8081
- Windows firewall prompt: allow Docker/Node for Private networks
- First run is slow: images and builds may take several minutes
- phpMyAdmin login: automan_user / automan_password

Done
Once the script finishes, the app is available at http://localhost:8080.