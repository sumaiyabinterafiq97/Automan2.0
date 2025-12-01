# How to Run Automan System - Client Instructions

## Prerequisites

Before starting, make sure you have:
- **Docker Desktop** installed and running
  - Download: https://www.docker.com/products/docker-desktop/
  - Start Docker Desktop and wait for it to fully load (green icon in system tray)

---

## For Windows Users

### Step 1: Download and Extract

1. **Download** the file: `automan-multiplatform-client-final-fixed-corrected.tar.gz`
2. **Extract** the file:
   - Right-click the file
   - Select "Extract All..." or use 7-Zip/WinRAR
   - Extract to a folder (e.g., `C:\Automan` or `E:\Automan`)
3. **Open** the extracted folder: `automan-multiplatform-client-final-fixed-corrected`

### Step 2: Run the System

**Option A: Double-Click (Easiest)**
1. Double-click `load-and-run.bat`
2. Wait 1-2 minutes for the system to start
3. The script will show progress messages

**Option B: Command Prompt**
1. Open Command Prompt or PowerShell
2. Navigate to the folder:
   ```cmd
   cd "E:\path\to\automan-multiplatform-client-final-fixed-corrected"
   ```
3. Run:
   ```cmd
   load-and-run.bat
   ```
4. Wait 1-2 minutes

### Step 3: Access the System

1. **Open your web browser**
2. **Go to**: http://localhost:8080
3. **Login**:
   - Email: `admin@automan.com`
   - Password: `password`

### Step 4: Verify It's Working

After logging in, you should see:
- Dashboard with system overview
- Sample data (1 client, 4 purchases)
- All features working correctly

---

## For MacBook Users

### Step 1: Download and Extract

1. **Download** the file: `automan-multiplatform-client-final-fixed-corrected.tar.gz`
2. **Extract** the file:
   - Double-click the `.tar.gz` file
   - Or use Terminal: `tar -xzf automan-multiplatform-client-final-fixed-corrected.tar.gz`
3. **Open Terminal** in the extracted folder

### Step 2: Run the System

1. **Open Terminal** in the extracted folder:
   ```bash
   cd ~/Downloads/automan-multiplatform-client-final-fixed-corrected
   ```
   (Adjust path to where you extracted the file)

2. **Make script executable** (first time only):
   ```bash
   chmod +x load-and-run.sh
   ```

3. **Run the script**:
   ```bash
   ./load-and-run.sh
   ```

4. **Wait** 1-2 minutes for the system to start

### Step 3: Access the System

1. **Open your web browser**
2. **Go to**: http://localhost:8080
3. **Login**:
   - Email: `admin@automan.com`
   - Password: `password`

### Step 4: Verify It's Working

After logging in, you should see:
- Dashboard with system overview
- Sample data (1 client, 4 purchases)
- All features working correctly

---

## Troubleshooting

### "Docker is not running"
**Solution:**
- Open Docker Desktop
- Wait for it to fully start (green icon)
- Try again

### "Port already in use"
**Solution:**
```cmd
# Windows (Command Prompt):
docker-compose down

# MacBook (Terminal):
docker-compose down
```
Then run the script again.

### "Cannot connect to Docker daemon"
**Solution:**
- Restart Docker Desktop
- Make sure Docker Desktop is fully started
- Try again

### "File not found" or "Image not found"
**Solution:**
- Make sure you're in the correct folder
- Verify `automan-multiplatform-client-final-fixed-corrected.tar` exists in the folder
- Check that you extracted all files

### "Permission denied" (MacBook)
**Solution:**
```bash
chmod +x load-and-run.sh
./load-and-run.sh
```

### System takes too long to start
**Solution:**
- This is normal! First startup takes 1-2 minutes
- Wait for the script to show "[SUCCESS] System is ready!"
- Then try accessing http://localhost:8080

---

## Management Commands

### Stop the System
```cmd
# Windows:
docker-compose down

# MacBook:
docker-compose down
```

### Restart the System
```cmd
# Windows:
docker-compose restart

# MacBook:
docker-compose restart
```

### View Logs
```cmd
# Windows:
docker-compose logs -f

# MacBook:
docker-compose logs -f
```

### Check Status
```cmd
# Windows:
docker-compose ps

# MacBook:
docker-compose ps
```

### Start Again (After Stopping)
```cmd
# Windows:
docker-compose up -d

# MacBook:
docker-compose up -d
```

---

## Access Points

- **Main Application**: http://localhost:8080
- **Backend API**: http://localhost:8083/api
- **Database Admin (phpMyAdmin)**: http://localhost:8084
- **MySQL Direct**: localhost:3306

---

## Login Credentials

- **Email**: `admin@automan.com`
- **Password**: `password`
- **Role**: ADMIN (full access)

---

## Pre-populated Data

The system comes with sample data:
- 1 Admin user
- 1 Client: Tokyo Auto Import
- 4 Sample purchases:
  - Honda Civic 2018
  - Toyota Prius 2015
  - Mercedes C-Class 2017
  - Honda Accord 2019

---

## Need Help?

If you encounter any issues:
1. Check the Troubleshooting section above
2. Make sure Docker Desktop is running
3. Verify all files are extracted correctly
4. Check container status: `docker-compose ps`
5. View logs: `docker-compose logs`

---

## Quick Start Summary

**Windows:**
1. Extract the package
2. Double-click `load-and-run.bat`
3. Wait 1-2 minutes
4. Open http://localhost:8080
5. Login: admin@automan.com / password

**MacBook:**
1. Extract the package
2. Open Terminal in the folder
3. Run: `./load-and-run.sh`
4. Wait 1-2 minutes
5. Open http://localhost:8080
6. Login: admin@automan.com / password

That's it! 🎉

