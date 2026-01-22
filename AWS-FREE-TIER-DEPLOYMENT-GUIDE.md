# AWS Free Tier Deployment Guide - Automan Car Purchase Management System

Complete step-by-step guide to deploy Automan on AWS using **100% free tier services** in **N. Virginia (us-east-1)** region. **Stay at $0/month for 12 months!**

## 🎯 Quick Start Recommendation

**💡 Use Docker Hub (Same as Todo App)!**

Your backend image is **~867MB** when built for production (much smaller than the 2.44GB you see locally, which includes build cache). **Solution**: Use **Docker Hub** (same approach as your todo app):

- ✅ **No size limits** (handles your ~867MB production image perfectly)
- ✅ **Works perfectly** (same as todo app deployment)
- ✅ **Free forever** (not just 12 months)
- ✅ **Simple setup** (just `docker login`)

**This guide uses Docker Hub exclusively** - the same approach that worked for your todo app!

## 📋 What You'll Need

- AWS account (create one at aws.amazon.com if you don't have one)
- About 45-60 minutes for the first deployment
- Domain name (optional but recommended - can use Route 53 or external)
- Docker installed locally (for building images)
- Basic knowledge of Linux commands

## ⚠️ Important: Free Tier Safety

**You can stay at $0/month** if you follow these rules:

### ✅ What IS Free (for 12 months after account creation in us-east-1):

- **EC2 t2.micro**: 750 hours/month free (enough for 1 instance running 24/7)
- **RDS db.t2.micro MySQL**: 750 hours/month free, 20GB storage
- **EBS Storage**: 30 GB free (you'll use ~20 GB)
- **Docker Hub**: Unlimited (public repos) or 1 private repo - **No size limits!**
- **Data Transfer OUT**: 15 GB/month free
- **Route 53**: First hosted zone free
- **ACM Certificate**: FREE (SSL certificates)
- **CloudFront**: 1 TB data transfer out free (for 12 months)
- **Elastic IP**: FREE (if attached to running instance)
- **EC2 Instance Connect**: FREE (browser-based terminal)

### ❌ What WILL Cost Money:

- **Multiple EC2 instances** (only 1 is free)
- **Exceeding 750 hours/month** (running multiple instances)
- **Exceeding 30 GB EBS storage**
- **Exceeding 20 GB RDS storage**
- **Exceeding 15 GB data transfer out** (without CloudFront)
- **Elastic IPs not attached** (~$3.60/month)
- **Application Load Balancer** (~$16/month - we're NOT using this)
- **Snapshots** (if you create them)
- **After 12 months**: Free tier expires, you'll pay ~$15-20/month

### 🛡️ How to Stay Safe:

1. **Set up billing alerts** (CRITICAL - do this first!)
2. **Use only 1 EC2 instance** (t2.micro)
3. **Use only 1 RDS instance** (db.t2.micro)
4. **Don't create Elastic IPs** unless attached to instance
5. **Don't create snapshots**
6. **Don't use ALB** (use CloudFront + EC2 directly)
7. **Monitor usage weekly** in AWS Billing Dashboard
8. **Stop instance when not testing** (saves free tier hours)

## 💰 Cost Estimate

**⚠️ IMPORTANT**: Free tier is only valid for **12 months** after account creation. After that, you'll be charged.

### Within Free Tier (First 12 Months):
- **EC2 t2.micro**: $0 (750 hours/month free - enough for 1 instance running 24/7)
- **RDS db.t2.micro**: $0 (750 hours/month free, 20GB storage)
- **EBS Storage (20 GB)**: $0 (within 30 GB free limit)
- **Docker Hub**: $0 (unlimited - no size limits!)
- **Data Transfer**: $0 (within 15 GB/month free, or 1TB with CloudFront)
- **Route 53**: $0 (first hosted zone free)
- **ACM Certificate**: $0 (free)
- **CloudFront**: $0 (1TB data transfer free for 12 months)
- **Elastic IP**: $0 (free when attached)
- **Total**: **$0/month** (within free tier limits)

### After Free Tier Expires (After 12 Months):
- **EC2 t2.micro**: ~$8-10/month (if running 24/7)
- **RDS db.t2.micro**: ~$15/month (if running 24/7)
- **EBS Storage (20 GB)**: ~$2/month
- **Docker Hub**: $0 (free forever - unlimited storage)
- **Data Transfer**: First 15 GB free, then ~$0.09/GB
- **Route 53**: ~$0.50/month per hosted zone
- **Total**: ~$25-30/month if running 24/7

**💡 To Minimize Costs After Free Tier**:
- **Stop EC2 and RDS when not using** (saves compute costs)
- Only pay for storage (~$2-3/month) when stopped
- Start instances only when testing/deploying

## 🏗️ Architecture (Free Tier Optimized)

```
┌─────────────────────────────────────────────────────────────┐
│                        Route 53 (DNS)                        │
│                    yourdomain.com                            │
│                    (First hosted zone FREE)                  │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    CloudFront CDN                             │
│                    (1TB transfer FREE)                      │
│                    HTTPS with ACM Certificate                │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    EC2 Instance (t2.micro)                   │
│                    (750 hours/month FREE)                    │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  Docker Containers:                                    │ │
│  │  • Frontend (Nginx) - Port 80                           │ │
│  │  • Backend (Spring Boot) - Port 8083                   │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              RDS MySQL (db.t2.micro)                         │
│              (750 hours/month FREE, 20GB storage)            │
│              Managed Database with Backups                   │
└─────────────────────────────────────────────────────────────┘
```

**Key Differences from Paid Tier**:
- ❌ No Application Load Balancer (too expensive)
- ✅ CloudFront CDN (free tier: 1TB transfer)
- ✅ Direct EC2 connection (via CloudFront)
- ✅ Smaller instance sizes (t2.micro instead of t3.medium)

---

## Step 1: Set Up Billing Alerts (DO THIS FIRST!)

**Why**: AWS will email you if you're about to be charged. This is your safety net!

### 1.1 Enable Billing Alerts

1. **Log into AWS Console** (console.aws.amazon.com)
2. In the top right, click your name → **Billing & Cost Management**
3. In the left sidebar, click **Billing Preferences**
4. Scroll down to **Billing alerts and cost anomaly detection**
5. Check these boxes:
   - ✅ **Receive Billing Alerts**
   - ✅ **Receive Free Tier Usage Alerts**
6. Enter your email address
7. Click **Save preferences**

### 1.2 Create CloudWatch Billing Alarm

1. In the AWS Console search bar (top), type **"CloudWatch"** and click it
2. In the left sidebar, click **Alarms** → **All alarms**
3. Click the orange **"Create alarm"** button
4. Click **"Select metric"**
5. In the search box, type **"Billing"**
6. Click **"Billing"** in the results
7. Click **"Total Estimated Charge"**
8. Select **"USD"** (United States Dollar)
9. Click **"Select metric"** button at the bottom
10. **Configure alarm**:
    - **Alarm name**: `Automan-FreeTier-BillingAlert`
    - **Threshold type**: Static
    - **Whenever EstimatedCharges is**: Greater than 0.01 USD
    - Click **"Next"**
11. **Configure actions**:
    - Click **"Create new SNS topic"**
    - **Topic name**: `automan-billing-alerts`
    - **Email**: Enter your email address
    - Click **"Create topic"**
    - Click **"Next"**
12. Click **"Next"** again
13. Click **"Create alarm"**
14. **Check your email** and confirm the subscription (click the link in the email)

**Done!** Now AWS will email you if charges occur.

---

## Step 2: Set Up Docker Hub

**Why Docker Hub?**
- ✅ **No storage limits** (handles your 2.43GB backend image perfectly!)
- ✅ **Works perfectly** (same approach as your todo app)
- ✅ **Free forever** (not just 12 months)
- ✅ **Simple setup** (just create account and login)

### 2.1 Create Docker Hub Account

1. Go to https://hub.docker.com
2. Sign up for a free account (if you don't have one)
3. Note your username (e.g., `yourusername`)

**Docker Hub Free Tier**:
- ✅ **1 private repository** OR **unlimited public repositories**
- ✅ **No storage limits** (your ~800-900MB production image works fine!)
- ✅ **Free forever** (not just 12 months)

### 2.2 Note Your Docker Hub Username

You'll use this format for images:
- Backend: `yourusername/automan-backend:latest`
- Frontend: `yourusername/automan-frontend:latest`

**✅ No size limits to worry about!** Your ~800-900MB production backend image will work perfectly.

---

## Step 3: Build and Push Docker Images to Docker Hub

**💡 Same Approach as Todo App - No Size Limits!**

This is the same approach that worked for your todo app. Docker Hub has no storage limits, so your ~800-900MB production backend image will work perfectly!

### 3.1 Login to Docker Hub

```bash
# On your local machine
docker login
# Enter your Docker Hub username and password
```

### 3.2 Build and Push Backend Image

```bash
# Navigate to project root
cd /Users/sumaiyabinterafiq/Development/Automan2.0

# Set your Docker Hub username
export DOCKERHUB_USERNAME=your-username  # Replace with your Docker Hub username

# Build backend image for linux/amd64 (EC2 architecture)
# You can use the regular Dockerfile - Docker Hub has no size limits!
docker build \
  --platform linux/amd64 \
  -t $DOCKERHUB_USERNAME/automan-backend:latest \
  -f backend/Dockerfile \
  backend/

# OR use optimized Dockerfile (smaller, but not required for Docker Hub):
# docker build --platform linux/amd64 -t $DOCKERHUB_USERNAME/automan-backend:latest -f backend/Dockerfile.optimized backend/

# Push to Docker Hub
docker push $DOCKERHUB_USERNAME/automan-backend:latest
```

**✅ Build completed successfully!** 

**Expected build time**: ~10-15 minutes (first build may take longer)

**Expected image size**: ~800-900MB (much smaller than the 2.43GB you saw locally - that included build cache!)

**Note**: The build may show a security warning about database passwords in the Dockerfile. This is expected - the credentials in the Dockerfile are just defaults for local development and will be overridden by docker-compose environment variables in production. The Dockerfile has been updated to remove hardcoded credentials for better security.

### 3.3 Build and Push Frontend Image

```bash
# IMPORTANT: Build Kotlin/JS frontend FIRST (required before Docker build)
./gradlew jsBrowserProductionWebpack

# Verify build output exists (should show index.html and JS files)
ls -la build/dist/js/productionExecutable/

# Build frontend image
docker build \
  --platform linux/amd64 \
  -t $DOCKERHUB_USERNAME/automan-frontend:latest \
  -f docker/Dockerfile.frontend.prod \
  .

# Push to Docker Hub
docker push $DOCKERHUB_USERNAME/automan-frontend:latest
```

**✅ Done!** Images are now on Docker Hub. No size limits to worry about!

**📌 Note**: You only need to push 2 images (backend + frontend). **No MySQL image is needed** because we're using AWS RDS (managed MySQL service) instead of a MySQL Docker container. This is better for production because:
- ✅ RDS handles backups automatically
- ✅ RDS provides high availability
- ✅ RDS is included in AWS free tier (750 hours/month)
- ✅ No need to manage MySQL container lifecycle

**⚠️ Important**: 
- **Always run `./gradlew jsBrowserProductionWebpack` BEFORE building the Docker image**
- If you see error: `"/build/dist/js/productionExecutable": not found`, it means you need to run the Gradle build first
- The `.dockerignore` file has been updated to allow the build output directory

### 3.4 Verify Images and Push to Docker Hub

```bash
# Check image sizes
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep automan

# Expected output:
# REPOSITORY              TAG       SIZE
# yourusername/automan-backend    latest    867MB    (works fine on Docker Hub!)
# yourusername/automan-frontend   latest    27.7MB

# Push backend image to Docker Hub (if not already pushed)
docker push $DOCKERHUB_USERNAME/automan-backend:latest

# Push frontend image to Docker Hub (after building in step 3.3)
docker push $DOCKERHUB_USERNAME/automan-frontend:latest

# Note: No MySQL image needed - we're using AWS RDS (managed MySQL service)
```

**✅ Images are now on Docker Hub and ready to deploy!**

**Build Notes**:
- **Backend build time**: ~10-15 minutes (first build may take longer)
- **Frontend build time**: ~20-30 seconds (Gradle) + ~5-10 seconds (Docker)
- **Image sizes**: ~867MB for backend, ~27.7MB for frontend (actual production sizes)
- **Security warning**: If you see a warning about database passwords, that's expected. The Dockerfile has been updated to remove hardcoded credentials - production credentials will be provided via docker-compose environment variables.
- **Frontend build requirement**: Always run `./gradlew jsBrowserProductionWebpack` BEFORE building the Docker image, otherwise you'll get a "not found" error. The `.dockerignore` file has been updated to allow the build output.

---

## Step 4: Create RDS MySQL Database (Free Tier)

### 4.1 Navigate to RDS

1. **AWS Console** → Search for **"RDS"** → Click **"RDS"**
2. In the left sidebar, click **"Databases"**
3. Click the orange **"Create database"** button
4. Make sure region is **"United States (N. Virginia) us-east-1"** (top right)

### 4.2 Choose Database Creation Method

1. Under **"Choose a database creation method"**, you have two options:
   - **"Easy create"** ✅ (Recommended - simpler, auto-configures most settings)
   - **"Full configuration"** (More control, but more steps - shows all options)
2. **Select "Easy create"** ✅
   - **Note**: With "Easy create", many settings are auto-configured based on your template selection
   - You'll only see: Engine type, Edition, DB instance size, DB identifier, Username, and Credentials management
   - Other settings (storage, backups, monitoring) are auto-configured

### 4.3 Configure Engine Options

1. **Engine type**: Select **"MySQL"** ✅ 
   - You'll see MySQL logo (orange/blue dolphin icon)
   - Should have a blue border when selected
2. **Edition**: **MySQL Community** (should be default/auto-selected)
3. **Engine version**: Will be auto-selected (e.g., MySQL 8.0.43 or latest)
   - **⚠️ IMPORTANT**: Do NOT enable "RDS Extended Support" (costs money)

### 4.4 Select DB Instance Size (CRITICAL FOR FREE TIER!)

**⚠️ THIS IS THE MOST IMPORTANT STEP - GET THIS WRONG AND YOU'LL BE CHARGED!**

1. Under **"DB instance size"**, you'll see three options:
   - **"Production"** ❌ 
     - Instance: `db.r7g.xlarge`
     - Cost: **1.915 USD/hour** (~$1,400/month!)
     - **DO NOT SELECT THIS!**
   
   - **"Dev/Test"** ❌ 
     - Instance: `db.r7g.large`
     - Cost: **0.271 USD/hour** (~$195/month!)
     - **DO NOT SELECT THIS!** (This is what's shown in your screenshot - it's NOT free!)
   
   - **"Sandbox"** ✅ **SELECT THIS ONE!**
     - Instance: `db.t4g.micro` (or `db.t2.micro` if available)
     - Cost: **0.019 USD/hour** (~$14/month) - **BUT FREE FOR 750 HOURS/MONTH!**
     - **This is free tier eligible!**

2. **Click "Sandbox"** ✅ - This automatically sets:
   - Instance class: `db.t4g.micro` or `db.t2.micro` (free tier eligible)
   - Storage: General Purpose SSD (gp2)
   - Allocated storage: 20 GB (free tier limit)
   - Single-AZ deployment (free tier)

**⚠️ CRITICAL WARNING**: 
- If you see **"Dev/Test"** selected (like in your screenshot), **CHANGE IT TO "SANDBOX"**!
- "Dev/Test" with `db.r7g.large` costs **$195/month** - NOT free tier!
- Only "Sandbox" with `db.t4g.micro` or `db.t2.micro` is free tier eligible!

### 4.5 Configure Availability and Durability

**Note**: With "Easy create" and "Sandbox" template, this section is **auto-configured** and not shown in the initial screens. It automatically selects Single-AZ deployment.

**What's auto-configured when you select "Sandbox"**:
- ✅ **Single-AZ DB instance deployment (1 instance)** (free tier)
- ❌ Multi-AZ is NOT selected (would cost extra)

**If you see this section** (in "Full configuration" mode):
- Select **"Single-AZ DB instance deployment (1 instance)"** ✅
- **DO NOT select** "Multi-AZ DB cluster deployment" (costs money!)
- **DO NOT select** "Multi-AZ DB instance deployment" (costs money!)

**Why Single-AZ?** Free tier only covers Single-AZ. Multi-AZ costs extra.

### 4.6 Configure Settings

1. **DB instance identifier**: 
   - Enter `automan-db` (or any unique name)
   - Must be 1-63 alphanumeric characters or hyphens
   - First character must be a letter
   - Cannot contain two consecutive hyphens
   - Cannot end with a hyphen

2. **Master username**: 
   - Enter `automan_admin` (or your preferred username)
   - Must be 1-16 alphanumeric characters
   - First character must be a letter

3. **Credentials management** (CRITICAL - Choose Correctly!):
   - **Option A (Recommended for Free Tier)**: Select **"Self managed"** ✅
     - Description: "Create your own password or have RDS create a password that you manage."
     - Click **"Create password"** button (RDS will generate one) OR enter your own strong password
     - **Save this password immediately!** You'll need it to connect to the database
     - **No additional charges** ✅
   
   - **Option B (NOT Recommended for Free Tier)**: "Managed in AWS Secrets Manager" ❌
     - Description: "RDS generates a password for you and manages it throughout its lifecycle using AWS Secrets Manager."
     - **⚠️ WARNING**: Blue banner states "additional charges apply" - See AWS Secrets Manager pricing
     - **Costs extra money** - not suitable for strict free tier
     - **DO NOT SELECT THIS** if you want to stay at $0/month

4. **Select the encryption key** (only shown if "Managed in AWS Secrets Manager" is selected):
   - **If you selected "Self managed"**: This section **will NOT appear** ✅ (that's correct)
   - **If you selected "Managed in AWS Secrets Manager"**: You'll see a dropdown
     - Leave default: `aws/secretsmanager (default)` ✅
   - **Note**: For free tier with "Self managed", encryption is still enabled (free) - it's just auto-configured

5. **Database authentication**: 
   - **With "Easy create"**: This is **auto-configured** to "Password authentication" ✅
   - **Not shown in initial screens** - it's automatically set correctly
   - This is correct for free tier

### 4.7 Configure Instance and Storage

**Note**: With "Easy create" and "Sandbox" template, these settings are **auto-configured** and **NOT shown** in the initial screens you see.

**What's auto-configured when you select "Sandbox"**:
- ✅ **DB instance class**: `db.t4g.micro` or `db.t2.micro` (free tier)
- ✅ **Storage type**: General Purpose SSD (gp2) (free tier)
- ✅ **Allocated storage**: 20 GB (free tier limit)
- ✅ **Storage autoscaling**: Disabled (to stay in free tier)

**You won't see these options in "Easy create" mode** - they're automatically set correctly based on your "Sandbox" selection.

**If you see these options** (in "Full configuration" mode):
1. **DB instance class**: Should show `db.t4g.micro` or `db.t2.micro` ✅
   - If it shows something else (like `db.m5d.large`), **go back and select "Sandbox"!**
2. **Storage type**: Should be **"General Purpose SSD (gp2)"** ✅
   - **DO NOT select** "Provisioned IOPS SSD (io2)" (costs money!)
3. **Allocated storage**: Should be **20 GB** ✅
   - **DO NOT increase** to 400 GB (costs money!)
4. **Storage autoscaling**: **Disabled** ❌

### 4.8 Configure Connectivity

**Note**: With "Easy create", most connectivity settings are **auto-configured**. You'll only see the EC2 connection option in the initial screens.

**What you'll see in "Easy create" mode**:

1. **Set up EC2 connection - optional** (This IS shown in your screenshots):
   - Under **"Compute resource"**, select **"Don't connect to an EC2 compute resource"** ✅
   - Description: "Don't set up a connection to a compute resource for this database. You can manually set up a connection to a compute resource later."
   - **Why?** We'll configure the security group manually for more control
   - **Note**: You can set up EC2 connection later from the database details page if needed

**What's auto-configured (NOT shown in initial screens)**:

2. **Virtual private cloud (VPC)**: 
   - Auto-selected: **"Default VPC"** ✅
   - Shows something like: "Default VPC (vpc-xxxxx)" with subnet count

3. **DB subnet group**: 
   - Auto-selected: **"default"** ✅
   - This defines which subnets the database can use

4. **Public access**: 
   - Auto-configured: **"No"** ✅ (more secure)
   - Only resources in your VPC (like your EC2 instance) can access the database
   - This is more secure and sufficient for our setup

5. **VPC security group (firewall)**: 
   - Auto-configured: Creates a new security group ✅
   - **Note**: We'll configure inbound rules later (in Step 6.4) to allow EC2 access

6. **RDS Proxy**: 
   - Auto-configured: **Not enabled** ❌ (costs extra - not needed for free tier)

### 4.9 Additional Settings (Auto-Configured with Easy Create)

**Note**: With "Easy create" and "Sandbox" template, these settings are **auto-configured** and **NOT shown** in the initial screens.

**What's auto-configured**:

1. **Database port**: `3306` (default MySQL port) ✅
2. **Initial database name**: You can specify this, or it will be auto-created
   - **Recommendation**: Enter `automan_car_purchase` if the option appears
3. **DB parameter group**: Auto-configured to default ✅
4. **Option group**: Auto-configured to default ✅
5. **Backup retention period**: Auto-configured to **7 days** ✅ (free tier allows this)
6. **Backup window**: Auto-configured to "No preference" ✅
7. **Enable encryption**: Auto-configured to **Enabled** ✅ (free - no additional cost)
8. **Performance Insights**: Auto-configured to **Disabled** ❌ (costs extra - not needed for free tier)
9. **Database Insights**: Auto-configured to **Standard** or **Disabled** ✅ (not Advanced)
10. **Enhanced Monitoring**: Auto-configured to **Disabled** ❌ (costs extra)
11. **Log exports**: Auto-configured to **All disabled** ❌ (CloudWatch Logs costs extra)

**Why these are disabled?** They add costs. For free tier, basic monitoring is sufficient.

**If you see these options** (in "Full configuration" mode):
- Follow the same guidelines above - keep everything at free tier defaults

### 4.12 Review and Create

1. Scroll down to see **"Estimated monthly costs"** section
2. **⚠️ CRITICAL: Verify the cost estimate!**
   - **Should show**: `$0.00` or very low cost (under $1/month)
   - **If you see**: `$1,477.05` or similar high cost, **STOP IMMEDIATELY!** ❌
   
3. **If cost is high, check these common mistakes** (based on screenshots):
   - ❌ **Template**: "Production" selected instead of "Free tier"
   - ❌ **Instance class**: `db.m5d.large` instead of `db.t2.micro`
   - ❌ **Storage type**: "Provisioned IOPS SSD (io2)" instead of "General Purpose SSD (gp2)"
   - ❌ **Storage size**: 400 GB instead of 20 GB
   - ❌ **IOPS**: 3000 IOPS provisioned (costs ~$900/month!)
   - ❌ **Deployment**: "Multi-AZ DB cluster (3 instances)" instead of "Single-AZ (1 instance)"
   - ❌ **Monitoring**: "Database Insights - Advanced" enabled (costs extra)
   - ❌ **Performance Insights**: Enabled (costs extra)
   
4. **To fix high costs**:
   - Go back to **Step 4.4** and select **"Sandbox"** (NOT "Dev/Test" or "Production")
   - Go back to **Step 4.6** and select **"Self managed"** for credentials (NOT "Managed in AWS Secrets Manager")
   - This will automatically fix most settings
   - Verify cost estimate shows $0.00 before proceeding
   
5. **Once cost shows $0.00**, click **"Create database"** button (orange button at bottom right)
6. **Wait 5-10 minutes** for database to be created
   - You'll see status: "Creating" → "Available"
   - Don't close the browser - wait for "Available" status

### 4.13 Note RDS Endpoint and Connection Details

After the database status changes to **"Available"**:
- **Endpoint**: `automan-db.xxxxx.us-east-1.rds.amazonaws.com`
- **Port**: 3306
- **Username**: `automan_admin`
- **Password**: [The one you set]

**Free Tier Limits**:
- ✅ 750 hours/month (enough for 24/7 operation)
- ✅ 20 GB storage
- ✅ 20 GB backup storage

---

## Step 5: Create EC2 Instance (Free Tier)

### 5.1 Navigate to EC2

1. **EC2 Console** → **Instances** → **Launch instance**
2. Make sure region is **"N. Virginia (us-east-1)"** (top right)

### 5.2 Create Security Group for EC2

1. **EC2 Console** → **Security Groups** → **Create security group**
2. **Settings**:
   - **Name**: `automan-ec2-sg`
   - **Description**: Security group for Automan EC2 instance
   - **VPC**: Default VPC
3. **Inbound rules**:
   - **HTTP (80)**: From `0.0.0.0/0` (CloudFront will connect)
   - **HTTPS (443)**: From `0.0.0.0/0` (CloudFront will connect)
   - **Note**: No SSH rule needed - we're using Session Manager (no IP configuration required!)
4. **Outbound rules**: Allow all (default)
5. Click **Create security group**

**Why no SSH?** Session Manager works through AWS Console - no SSH keys or IP configuration needed!

### 5.3 Create IAM Role for EC2 (REQUIRED for Session Manager)

**⚠️ IMPORTANT**: This IAM role is **REQUIRED** for Session Manager to work. Without it, you won't be able to connect!

1. **IAM Console** → **Roles** → **Create role**
2. **Trusted entity type**: AWS service
3. **Use case**: EC2 → Click **Next**
4. **Permissions**: Search for and attach:
   - `AmazonSSMManagedInstanceCore` ✅ (REQUIRED for Session Manager)
   - `CloudWatchAgentServerPolicy` (optional - for logging)
5. Click **Next**
6. **Role name**: `AutomanEC2SessionManagerRole`
7. **Description**: IAM role for Automan EC2 instance to use Session Manager
8. Click **Create role**

**Why this is needed**: Session Manager requires SSM (Systems Manager) permissions to connect to your instance.

### 5.4 Launch EC2 Instance

1. **EC2 Console** → **Instances** → **Launch instance**
2. **Name**: `automan-free-tier`
3. **AMI**: **Amazon Linux 2023** (latest)
4. **Instance type**: **t2.micro** ✅ (free tier eligible)
   - **Note**: Should show "Free tier eligible" label
5. **Key pair (login)**: 
   - **Select "No key pair"** ✅ (we're using Session Manager - no SSH key needed!)
   - **Note**: Session Manager works through AWS Console, so no key pair is required
6. **Network settings**:
   - **VPC**: Default VPC
   - **Subnet**: Public subnet
   - **Auto-assign public IP**: Enable ✅
   - **Security group**: `automan-ec2-sg`
7. **Configure storage**:
   - **Size**: 20 GB gp3 ✅ (within 30 GB free limit)
   - **Delete on termination**: Unchecked ✅ (keep data)
8. **Advanced details**:
   - **IAM instance profile**: Select `AutomanEC2SessionManagerRole` ✅ (REQUIRED for Session Manager)
   - **User data** (paste this script):

```bash
#!/bin/bash
# Update system
yum update -y

# Install Docker
yum install docker -y
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Install and start SSM Agent (REQUIRED for Session Manager)
yum install amazon-ssm-agent -y
systemctl enable amazon-ssm-agent
systemctl start amazon-ssm-agent

# Install AWS CLI (optional - for CloudWatch logging)
yum install aws-cli -y

# Install Git
yum install git -y

# Install MySQL client
yum install mysql -y

# Create app directory
mkdir -p /home/ec2-user/automan
chown ec2-user:ec2-user /home/ec2-user/automan
```

9. Click **Launch instance**

### 5.5 Wait for Instance to Start

Wait 2-3 minutes for:
- Instance state: **Running** ✅
- Status check: **2/2 checks passed** ✅

**Note**: 
- **Instance ID**: e.g., `i-03bd68ff192809792` (you'll need this for Session Manager)
- **Public IPv4 address**: Not needed for Session Manager, but useful for direct access
- **SSM Agent status**: Should show "Online" after 1-2 minutes (check in Systems Manager → Fleet Manager)

### 5.6 Allocate Elastic IP (Free if Attached)

1. **EC2 Console** → **Elastic IPs** → **Allocate Elastic IP address**
2. **Network border group**: us-east-1
3. Click **Allocate**
4. **Select the Elastic IP** → **Actions** → **Associate Elastic IP address**
5. **Instance**: Select `automan-free-tier`
6. Click **Associate**

**Note**: Elastic IP is FREE as long as it's attached to a running instance.

---

## Step 6: Connect to EC2 and Deploy Application

### 6.1 Connect via Session Manager (No SSH Keys Needed!)

**Why Session Manager?**
- ✅ No SSH keys needed
- ✅ No IP address configuration needed
- ✅ Works through AWS Console (browser-based terminal)
- ✅ More secure (no open SSH ports)
- ✅ Works even if your IP changes

**Steps to Connect:**

1. **EC2 Console** → **Instances** → Select your instance (`automan-free-tier`)
2. Click the orange **"Connect"** button (top right)
3. Click the **"Session Manager"** tab (second tab)
4. Click the orange **"Connect"** button
5. **You'll get a browser-based terminal!** 🎉

**If you see "SSM Agent is not online"**:
- Wait 1-2 more minutes for SSM Agent to start
- Check Systems Manager → Fleet Manager → Your instance should show "Online"
- If still offline, verify IAM role is attached correctly

**Note**: Session Manager is **FREE** - no additional charges!

### 6.2 Verify and Install Docker (If Needed)

**⚠️ IMPORTANT**: If you see "docker: command not found", the User Data script might still be running or Docker might not be installed. Follow these steps:

```bash
# Step 1: Check if Docker is installed
which docker
# OR
docker --version

# If Docker is NOT found, install it:
sudo yum update -y
sudo yum install docker -y

# Step 2: Start Docker service
sudo systemctl start docker
sudo systemctl enable docker

# Step 3: Add ec2-user to docker group (if not already done)
sudo usermod -aG docker ec2-user

# Step 4: Verify Docker is working
sudo docker --version
sudo docker ps

# Step 5: Check Docker service status
sudo systemctl status docker
```

**If Docker is installed but you still get "command not found"**:
- The User Data script might still be running (wait 2-3 more minutes)
- Try using `sudo docker` instead of just `docker`
- Log out and reconnect via Session Manager (group changes need a new session)

### 6.3 Install Docker Compose (If Needed)

```bash
# Check if Docker Compose is installed
docker-compose --version

# If not installed:
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Verify
sudo docker-compose --version
```

### 6.4 Login to Docker Hub

```bash
# Login to Docker Hub (use sudo if needed)
sudo docker login
# Enter your Docker Hub username and password

# Verify login worked
sudo docker images
```

### 6.5 Upload Project Files to EC2

You have two options. **Choose Option A if your code is already on GitHub**, or **Option B if your GitHub doesn't have the latest code** (recommended for you).

---

#### Option A: Push to GitHub First, Then Clone (If Code is Already on GitHub)

**If your GitHub repository already has the latest code**, skip to Step 2.

**Step 1: Push Your Latest Code to GitHub (If Needed)**

On your local machine:

```bash
# Navigate to your project directory
cd /Users/sumaiyabinterafiq/Development/Automan2.0

# Check if Git is initialized
git status

# If not initialized, initialize Git:
git init
git add .
git commit -m "Initial commit: Automan project"

# Add GitHub as remote (replace YOUR_USERNAME and REPO_NAME)
git remote add origin https://github.com/YOUR_USERNAME/REPO_NAME.git
# OR if remote already exists, update it:
# git remote set-url origin https://github.com/YOUR_USERNAME/REPO_NAME.git

# Push to GitHub
git branch -M main
git push -u origin main
```

**If asked for credentials**:
- GitHub no longer accepts passwords for HTTPS
- Use a **Personal Access Token**:
  1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
  2. Click **"Generate new token (classic)"**
  3. Name: "AWS Deployment"
  4. Select scope: **"repo"** (full control)
  5. Click **"Generate token"**
  6. **Copy the token** (you won't see it again!)
  7. When `git push` asks for password, paste the token instead

**Step 2: Clone on EC2** (via Session Manager):

```bash
# Install Git (if not already installed)
sudo yum install git -y

# Navigate to home directory
cd /home/ec2-user

# Clone your repository (replace with your actual GitHub URL)
git clone https://github.com/YOUR_USERNAME/REPO_NAME.git automan

# Navigate to project directory
cd automan
```

---

#### Option B: Upload via S3 (Recommended - If GitHub Doesn't Have Latest Code)

**This is the easiest option if your GitHub doesn't have the latest code!**

**Step 1: Create Zip File on Your Local Machine**

```bash
# Navigate to your project directory
cd /Users/sumaiyabinterafiq/Development/Automan2.0

# Create zip file (excludes git, build files, etc.)
zip -r automan-project.zip . \
  -x "*.git*" \
  -x "*node_modules*" \
  -x "*build*" \
  -x "*.gradle*" \
  -x "*.pem" \
  -x "*.zip" \
  -x "*kotlin-js-store*" \
  -x "*backend/build*"
```

**Step 2: Upload to S3**

**💰 Cost**: FREE! S3 free tier includes 5 GB storage and 2,000 PUT requests.

1. **Go to S3 Console**:
   - AWS Console → Search "S3" → Click "S3"

2. **Create bucket** (if you don't have one):
   - Click **"Create bucket"**
   - **Bucket name**: `automan-deploy-2026` (or any unique name)
   - **Region**: `us-east-1` (N. Virginia)
   - **Block Public Access**: Keep checked ✅ (for security)
   - Click **"Create bucket"**

3. **Upload zip file**:
   - Click on your bucket name
   - Click **"Upload"**
   - Click **"Add files"** or drag and drop `automan-project.zip`
   - Click **"Upload"** at bottom
   - Wait for "Upload succeeded"

**Step 3: Download on EC2** (via Session Manager):

```bash
# Install AWS CLI (if not already installed)
sudo yum install aws-cli -y

# Navigate to home directory
cd /home/ec2-user

# Download from S3 (replace YOUR_BUCKET_NAME with your actual bucket name)
aws s3 cp s3://YOUR_BUCKET_NAME/automan-project.zip /home/ec2-user/automan-project.zip

# Install unzip (if not already installed)
sudo yum install unzip -y

# Extract
unzip automan-project.zip -d automan

# Navigate to project directory
cd automan
```

---

### 6.6 Create Environment File

After uploading files (via Git or S3), create the environment file:

```bash
# Make sure you're in the project directory
cd /home/ec2-user/automan

# Create environment file for production
cat > docker/.env.prod << EOF
DOCKERHUB_USERNAME=your-username
RDS_ENDPOINT=automan-db.xxxxx.us-east-1.rds.amazonaws.com
RDS_USERNAME=automan_admin
RDS_PASSWORD=YOUR_RDS_PASSWORD_HERE
EOF

# Verify the file was created
cat docker/.env.prod
```

### 6.7 Update RDS Security Group

1. **EC2 Console** → **Security Groups** → `automan-db-sg`
2. **Edit inbound rules**:
   - **Type**: MySQL/Aurora
   - **Port**: 3306
   - **Source**: Select `automan-ec2-sg` security group
   - Click **Save rules**

### 6.8 Initialize Database

```bash
# Connect to RDS and run migrations
# Replace with your RDS endpoint and password
export RDS_ENDPOINT=automan-db.xxxxx.us-east-1.rds.amazonaws.com
export RDS_USER=automan_admin
export RDS_PASSWORD=YOUR_PASSWORD

# Run all migrations
for file in database/*.sql; do
  echo "Running $file..."
  mysql -h $RDS_ENDPOINT \
        -u $RDS_USER \
        -p$RDS_PASSWORD \
        automan_car_purchase < "$file"
done
```

### 6.9 Create Production Docker Compose File

**📌 Note**: This docker-compose file only includes **backend and frontend containers**. **No MySQL container** because we're using AWS RDS (managed MySQL service) instead. The backend connects to RDS using the RDS endpoint.

```bash
cd /home/ec2-user/automan

# Set your Docker Hub username
export DOCKERHUB_USERNAME=your-username  # Replace with your Docker Hub username

# Create docker-compose.hub.yml (for Docker Hub)
# Note: Only 2 services (backend + frontend) - MySQL is handled by AWS RDS
cat > docker/docker-compose.hub.yml << EOF
version: '3.8'

services:
  backend:
    image: ${DOCKERHUB_USERNAME}/automan-backend:latest
    container_name: automan_backend_prod
    restart: always
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      # Connecting to AWS RDS MySQL (managed service, NOT a Docker container)
      SPRING_DATASOURCE_URL: jdbc:mysql://\${RDS_ENDPOINT}:3306/automan_car_purchase?useSSL=true&requireSSL=true&serverTimezone=UTC
      SPRING_DATASOURCE_USERNAME: \${RDS_USERNAME}
      SPRING_DATASOURCE_PASSWORD: \${RDS_PASSWORD}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/api/purchases"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - automan_network

  frontend:
    image: ${DOCKERHUB_USERNAME}/automan-frontend:latest
    container_name: automan_frontend_prod
    restart: always
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - automan_network

# Note: No MySQL service - using AWS RDS (managed MySQL service) instead
networks:
  automan_network:
    driver: bridge
EOF

# Create environment file
cat > docker/.env.prod << EOF
DOCKERHUB_USERNAME=$DOCKERHUB_USERNAME
RDS_ENDPOINT=automan-db.xxxxx.us-east-1.rds.amazonaws.com
RDS_USERNAME=automan_admin
RDS_PASSWORD=YOUR_RDS_PASSWORD_HERE
EOF

# Load environment variables
source docker/.env.prod

# Pull latest images from Docker Hub (fast - no building!)
docker-compose -f docker/docker-compose.hub.yml pull

# Start services
docker-compose -f docker/docker-compose.hub.yml up -d

# Check status
docker ps
docker-compose -f docker/docker-compose.hub.yml logs
```

---

## Step 7: Set Up Domain and SSL Certificate (Free)

### 7.1 Request ACM Certificate

1. **ACM Console** → **Request a certificate**
2. Make sure region is **"N. Virginia (us-east-1)"** (CloudFront requires us-east-1)
3. **Domain names**:
   - **Fully qualified domain name**: `yourdomain.com`
   - **Add another name**: `*.yourdomain.com` (wildcard)
4. **Validation method**: **DNS validation** ✅
5. Click **Request**
6. **Add DNS records** to your domain:
   - ACM will show CNAME records to add
   - Add them to your domain's DNS (Route 53 or external provider)
   - Wait 5-10 minutes for validation

### 7.2 Set Up Route 53 Hosted Zone (Free Tier)

1. **Route 53 Console** → **Hosted zones** → **Create hosted zone**
2. **Domain name**: `yourdomain.com`
3. **Type**: Public hosted zone ✅
4. Click **Create**
5. **Note the NS records** - update your domain registrar with these nameservers

**Free Tier**: First hosted zone is FREE!

### 7.3 Create Route 53 Records

1. **Route 53** → **Hosted zones** → `yourdomain.com` → **Create record**
2. **Record 1 - EC2 A Record**:
   - **Record name**: `@` (or leave blank for root domain)
   - **Record type**: A
   - **Value**: Your Elastic IP address
   - **TTL**: 300
   - Click **Create records**
3. **Record 2 - www subdomain**:
   - **Record name**: `www`
   - **Record type**: A
   - **Value**: Your Elastic IP address
   - **TTL**: 300
   - Click **Create records**

---

## Step 8: Set Up CloudFront CDN (Free Tier - 1TB Transfer)

### 8.1 Create CloudFront Distribution

1. **CloudFront Console** → **Create distribution**
2. **Origin settings**:
   - **Origin domain**: Your EC2 Elastic IP (e.g., `54.123.45.67`)
   - **Origin path**: Leave blank
   - **Name**: Auto-generated
   - **Origin protocol**: HTTP Only (we'll add SSL at CloudFront level)
3. **Default cache behavior**:
   - **Viewer protocol policy**: Redirect HTTP to HTTPS ✅
   - **Allowed HTTP methods**: GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE ✅
   - **Cache policy**: CachingOptimized
   - **Origin request policy**: CORS-S3Origin (or create custom)
4. **Distribution settings**:
   - **Price class**: Use only North America and Europe (cheaper)
   - **Alternate domain names (CNAMEs)**: `yourdomain.com`, `www.yourdomain.com`
   - **SSL certificate**: Select your ACM certificate (from us-east-1)
   - **Default root object**: `index.html`
5. Click **Create distribution**

**Wait 15-20 minutes** for distribution to deploy.

**Free Tier**: 1 TB data transfer out free for 12 months!

### 8.2 Update Route 53 to Point to CloudFront

1. **Route 53** → **Hosted zones** → `yourdomain.com`
2. **Edit A record**:
   - **Record name**: `@`
   - **Record type**: A
   - **Alias**: Yes ✅
   - **Route traffic to**: Alias to CloudFront distribution
   - **Distribution**: Select your CloudFront distribution
   - Click **Save changes**
3. **Edit www record**:
   - Same as above, but for `www` subdomain

---

## Step 9: Set Up Auto-Start on Reboot

### 9.1 Create Systemd Service

```bash
# On EC2
sudo nano /etc/systemd/system/automan.service
```

Paste:

```ini
[Unit]
Description=Automan Car Purchase System
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ec2-user/automan
ExecStart=/usr/local/bin/docker-compose -f docker/docker-compose.hub.yml up -d
ExecStop=/usr/local/bin/docker-compose -f docker/docker-compose.hub.yml down
User=ec2-user
Group=docker
Environment="DOCKERHUB_USERNAME=YOUR_DOCKERHUB_USERNAME"

[Install]
WantedBy=multi-user.target
```

### 9.2 Enable Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable automan.service
sudo systemctl start automan.service
sudo systemctl status automan.service
```

---

## Step 10: Verify Deployment

### 10.1 Test Application

1. **Access via CloudFront**: `https://yourdomain.com`
2. **Access via Elastic IP**: `http://YOUR_ELASTIC_IP` (direct, no SSL)
3. **Test API**: `https://yourdomain.com/api/purchases`

### 10.2 Check Free Tier Usage

1. **AWS Console** → **Billing** → **Free Tier**
2. Check usage:
   - **EC2 hours**: Should be < 750/month
   - **RDS hours**: Should be < 750/month
   - **EBS storage**: Should be < 30 GB
   - **Data transfer**: Should be < 15 GB (or 1TB with CloudFront)

---

## Troubleshooting

### Docker Command Not Found in Session Manager

**Error**: `docker: command not found` or `bash: docker: command not found`

**Symptoms**:
- You connect via Session Manager
- Try to run `docker login` or `docker --version`
- Get "command not found" error

**Causes**:
1. User Data script is still running (takes 3-5 minutes after instance launch)
2. Docker is not installed
3. Docker service is not started
4. PATH doesn't include docker (rare)

**Solution**:

**Step 1: Check if Docker is installed**
```bash
which docker
# OR
sudo docker --version
```

**Step 2: If Docker is NOT installed, install it**
```bash
sudo yum update -y
sudo yum install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user
```

**Step 3: Verify Docker is working**
```bash
sudo docker --version
sudo docker ps
```

**Step 4: Check Docker service status**
```bash
sudo systemctl status docker
```

**Step 5: If Docker service is not running, start it**
```bash
sudo systemctl start docker
sudo systemctl enable docker
```

**Step 6: Always use `sudo` with docker commands in Session Manager**
```bash
# Correct way:
sudo docker login
sudo docker ps
sudo docker-compose --version

# Wrong way (will fail):
docker login
docker ps
```

**Why use `sudo`?** Session Manager runs as `ssm-user`, not `ec2-user`. Even though `ec2-user` is in the docker group, `ssm-user` needs `sudo` to run docker commands.

**Prevention**: Wait 3-5 minutes after launching the instance for User Data script to complete. Check instance status in EC2 Console - it should show "2/2 checks passed" when ready.

---

### Frontend Docker Build Fails with "not found" Error

**Error**: `"/build/dist/js/productionExecutable": not found`

**Cause**: The frontend build output doesn't exist or `.dockerignore` is excluding it.

**Solution**:
1. **Run Gradle build first**:
   ```bash
   ./gradlew jsBrowserProductionWebpack
   ```

2. **Verify build output exists**:
   ```bash
   ls -la build/dist/js/productionExecutable/
   # Should show: index.html, automan-car-purchase.js, etc.
   ```

3. **Check `.dockerignore` file**:
   - Make sure it has: `!build/dist/js/productionExecutable/`
   - This allows Docker to access the build output

4. **Rebuild Docker image**:
   ```bash
   docker build --platform linux/amd64 -t $DOCKERHUB_USERNAME/automan-frontend:latest -f docker/Dockerfile.frontend.prod .
   ```

**Prevention**: Always run `./gradlew jsBrowserProductionWebpack` BEFORE building the Docker image.

---

### Application Not Accessible

1. **Check EC2 status**: EC2 Console → Instances → Status checks
2. **Check containers**: `docker ps` on EC2
3. **Check logs**: `docker-compose -f docker/docker-compose.prod.yml logs`
4. **Check security groups**: Ensure HTTP (80) is open from CloudFront

### Database Connection Errors

1. **Check RDS security group**: Must allow EC2 security group
2. **Check RDS endpoint**: Verify in docker-compose.hub.yml
3. **Test connection**: `mysql -h RDS_ENDPOINT -u USERNAME -p`

### CloudFront Not Working

1. **Check distribution status**: CloudFront Console → Distributions
2. **Wait for deployment**: Takes 15-20 minutes
3. **Check origin**: Ensure EC2 is accessible on port 80
4. **Check SSL certificate**: Must be in us-east-1 region

### High Costs (Should Be $0)

**⚠️ If you see high costs (like $1,477.05/month), this is a CRITICAL issue!**

**Common RDS Database Mistakes (Based on Screenshots):**

1. **❌ Wrong DB Instance Size Selected (MOST COMMON)**:
   - **Problem**: "Dev/Test" selected with `db.r7g.large` (shown in your screenshots)
   - **Cost**: **0.271 USD/hour = ~$195/month!**
   - **Fix**: Must select **"Sandbox"** with `db.t4g.micro` or `db.t2.micro` (free tier)

2. **❌ Wrong Template Selected**:
   - **Problem**: "Production" template selected with `db.r7g.xlarge`
   - **Cost**: **1.915 USD/hour = ~$1,400/month!**
   - **Fix**: Delete database → Recreate with **"Sandbox"** template

3. **❌ Secrets Manager Selected (Costs Extra)**:
   - **Problem**: "Managed in AWS Secrets Manager" selected (shown in your screenshot)
   - **Cost**: Additional charges (see AWS Secrets Manager pricing)
   - **Fix**: Select **"Self managed"** for credentials (free)

4. **❌ Wrong Instance Class**:
   - **Problem**: `db.r7g.large` or `db.m5d.large` selected
   - **Cost**: $195-427/month
   - **Fix**: Must be `db.t4g.micro` or `db.t2.micro` (free tier)

3. **❌ Wrong Storage Type**:
   - **Problem**: "Provisioned IOPS SSD (io2)" selected (shown in screenshot)
   - **Cost**: ~$150/month for storage + $900/month for IOPS = $1,050/month!
   - **Fix**: Must be "General Purpose SSD (gp2)" (free tier)

4. **❌ Too Much Storage**:
   - **Problem**: 400 GB allocated (shown in screenshot)
   - **Cost**: ~$150/month
   - **Fix**: Must be 20 GB (free tier limit)

5. **❌ Provisioned IOPS**:
   - **Problem**: 3000 IOPS provisioned (shown in screenshot)
   - **Cost**: ~$900/month
   - **Fix**: Don't use Provisioned IOPS - use gp2 storage instead

6. **❌ Multi-AZ Deployment**:
   - **Problem**: "Multi-AZ DB cluster (3 instances)" selected (shown in screenshot)
   - **Cost**: 3x instance cost
   - **Fix**: Must be "Single-AZ DB instance (1 instance)" (free tier)

7. **❌ Advanced Monitoring Enabled**:
   - **Problem**: "Database Insights - Advanced" selected (shown in screenshot)
   - **Cost**: Additional charges
   - **Fix**: Disable or use "Standard" (or disable completely)

8. **❌ Performance Insights Enabled**:
   - **Problem**: Performance Insights checkbox checked (shown in screenshot)
   - **Cost**: Additional charges
   - **Fix**: Uncheck Performance Insights

**How to Fix High Costs:**

1. **If database is still "Creating"**: Cancel/delete it immediately
2. **If database is "Available"**: 
   - RDS Console → Databases → Select database → Actions → Delete
   - **Important**: Uncheck "Create final snapshot" (saves money)
   - Confirm deletion
3. **Recreate with correct settings**:
   - Follow Step 4 again, but **select "Sandbox"** for DB instance size (NOT "Dev/Test" or "Production")
   - Select **"Self managed"** for credentials (NOT "Managed in AWS Secrets Manager")
   - Verify cost estimate shows **$0.00** before clicking "Create database"

**Other Cost Issues:**

- **EC2**: Multiple instances or wrong type (t3.medium instead of t2.micro)
- **Application Load Balancer**: Not free tier - don't use it
- **CloudWatch Logs**: If enabled for RDS, disable it
- **Secrets Manager**: If using managed credentials, switch to self-managed

**Verify Free Tier Status:**

1. **Check billing dashboard**: AWS Console → Billing → Cost Explorer
2. **Verify free tier eligibility**: Account creation date (must be within 12 months)
3. **Review CloudWatch alarms**: Should alert if charges occur
4. **Check RDS console**: Database → Configuration tab → Verify instance class is `db.t4g.micro` or `db.t2.micro`
5. **Check credentials**: Verify "Self managed" is selected (NOT "Managed in AWS Secrets Manager")

---

## Cost Optimization Tips (Stay at $0)

1. ✅ **Use only t2.micro EC2** (free tier)
2. ✅ **Use only db.t2.micro RDS** (free tier)
3. ✅ **Keep EBS under 30 GB** (free tier limit)
5. ✅ **Use CloudFront** (1TB free for 12 months)
6. ✅ **Attach Elastic IP** (free when attached)
7. ✅ **Don't use ALB** (not free tier)
8. ✅ **Stop instances when not testing** (saves free tier hours)
9. ✅ **Monitor weekly** in AWS Billing Dashboard

---

## Summary

✅ **Deployed**:
- EC2 t2.micro instance (free tier) - runs backend + frontend containers
- RDS db.t2.micro MySQL (free tier) - **managed database service, NOT a Docker container**
- CloudFront CDN (1TB free for 12 months)
- Route 53 domain (first hosted zone free)
- ACM SSL certificate (free)
- Docker Hub (no size limits - handles your ~800-900MB production image perfectly!)
- Session Manager for EC2 access (no SSH keys needed!)
- Auto-start on reboot

**📌 Docker Images Pushed to Docker Hub**:
- ✅ Backend image: `yourusername/automan-backend:latest`
- ✅ Frontend image: `yourusername/automan-frontend:latest`
- ❌ **No MySQL image** - Using AWS RDS (managed MySQL service) instead of a Docker container
- ❌ **No MySQL image** (using AWS RDS managed service instead)

✅ **Access**:
- **Production URL**: `https://yourdomain.com`
- **Backend API**: `https://yourdomain.com/api`
- **Direct IP**: `http://YOUR_ELASTIC_IP` (no SSL)

✅ **Monthly Cost**: **$0/month** (within free tier limits for 12 months)

✅ **Free Tier Limits**:
- EC2: 750 hours/month ✅
- RDS: 750 hours/month, 20GB storage ✅
- EBS: 30 GB ✅
- Docker Hub: No limits ✅ (handles your 2.43GB image perfectly!)
- Data Transfer: 15 GB/month (or 1TB with CloudFront) ✅
- Route 53: First hosted zone free ✅

✅ **Next Steps**:
- Monitor free tier usage weekly
- Set up CloudWatch alarms
- Review costs monthly
- Plan for post-free-tier (after 12 months)

---

## Quick Reference

### Important URLs and Endpoints

- **Production URL**: `https://yourdomain.com`
- **Backend API**: `https://yourdomain.com/api`
- **EC2 Elastic IP**: `http://YOUR_ELASTIC_IP`
- **RDS Endpoint**: `automan-db.xxxxx.us-east-1.rds.amazonaws.com:3306`
- **Docker Hub Backend**: `yourusername/automan-backend:latest`
- **Docker Hub Frontend**: `yourusername/automan-frontend:latest`
- **Connection Method**: Session Manager (EC2 Console → Instances → Connect → Session Manager)

### Common Commands

**On EC2 Instance (via Session Manager)**:
```bash
# Connect via Session Manager first:
# EC2 Console → Instances → Your Instance → Connect → Session Manager → Connect

# View logs
sudo docker-compose -f docker/docker-compose.hub.yml logs -f

# Restart services
sudo docker-compose -f docker/docker-compose.hub.yml restart

# Pull latest images and restart (from Docker Hub)
sudo docker-compose -f docker/docker-compose.hub.yml pull
sudo docker-compose -f docker/docker-compose.hub.yml up -d

# Check container status
sudo docker ps
```

**Note**: Use `sudo` with docker commands in Session Manager if you get permission errors.

**On Local Machine (Rebuild and Push to Docker Hub)**:
```bash
# Set your Docker Hub username
export DOCKERHUB_USERNAME=your-username

# Login to Docker Hub
docker login

# Build and push backend
docker build --platform linux/amd64 -t $DOCKERHUB_USERNAME/automan-backend:latest -f backend/Dockerfile backend/
docker push $DOCKERHUB_USERNAME/automan-backend:latest

# Build and push frontend
# IMPORTANT: Run Gradle build FIRST, then Docker build
./gradlew jsBrowserProductionWebpack
docker build --platform linux/amd64 -t $DOCKERHUB_USERNAME/automan-frontend:latest -f docker/Dockerfile.frontend.prod .
docker push $DOCKERHUB_USERNAME/automan-frontend:latest

# Then on EC2, pull and restart:
# docker-compose -f docker/docker-compose.hub.yml pull
# docker-compose -f docker/docker-compose.hub.yml up -d
```

### Free Tier Checklist

- ✅ EC2 t2.micro (750 hours/month)
- ✅ RDS db.t2.micro (750 hours/month, 20GB)
- ✅ EBS 20GB (within 30GB limit)
- ✅ Docker Hub (no size limits - handles your 2.43GB image perfectly!)
- ✅ CloudFront 1TB transfer (12 months)
- ✅ Route 53 first hosted zone
- ✅ ACM certificate
- ✅ Elastic IP (attached)
- ✅ Docker Hub (no size limits - handles your 2.43GB image perfectly!)
- ❌ No ALB (not free tier)
- ❌ No larger instances


---

**Congratulations! Your Automan application is now live on AWS Free Tier! 🎉**

**Remember**: Free tier is valid for 12 months after account creation. Monitor usage weekly to stay at $0/month!
