# Automan Car Purchase Management System

A comprehensive car purchase management system built with Kotlin JS Compose for the frontend and Spring Boot for the backend, with MySQL database integration. The system is containerized using Docker for easy deployment across multiple platforms (Windows, macOS, Linux).

## 🆕 Recent Updates

### Latest Changes (February 2026)
- **Email & Signup Flow**:
  - Switched from SendGrid to **Resend** for signup approval emails
  - Admin approval workflow: signup requests → admin approves/rejects → verification emails sent via Resend
  - `pending_signups` table for email verification tokens
- **Master Data & UI**:
  - **Supplier Master** fully implemented (add/edit/delete/duplicate, column filters, pagination)
  - Supplier dropdown auto-refresh when suppliers are added/edited in master tab
  - Label change: "Shipment Size/Type of Vehicle" → **Vehicle type**
  - Duplicate button in Edit Supplier modal
- **Purchase Form**:
  - Total Cost (Before/After Tax) now includes **Rixo Price**
  - Production date validation relaxed (no year range restriction; supports antique cars)
  - Chassis dropdown preserves suffix when selecting (e.g. `B43W-t6yg` stays intact)
  - Car brand updates correctly when editing in Car Brand modal
- **Deployment Scripts**:
  - `./scripts/build-and-deploy-frontend.sh` – bump version, build Kotlin/JS, build Docker image, recreate frontend container (uses compose for correct network)
  - `./scripts/rebuild-and-restart-backend.sh` – rebuild backend image and restart container
- **Database Consolidation**:
  - All SQL files consolidated into single `01-init-multiplatform.sql`
  - Contains all table definitions and essential seed data
  - Comments explain why each INSERT is required

### Previous Updates (January 2026)
- **Security Improvements**: XSS protection, safe localStorage, improved error handling
- **Code Quality**: Centralized constants in `AppConstants`, Logger utilities, race condition fixes
- **Bug Fixes**: Memory leaks, form population races, date parsing, null safety
- **Sidebar Navigation**: Renamed "Purchase List" to "Home", "Rixo Request" to "Rixo Note", added Shipment/Sales/Reports sections

### Earlier Updates
- **Database Migration Consolidation**: Streamlined database migrations into 8 main files for easier setup and maintenance
- **Invoice Page Redesign**: New invoice workflow with CLIENT (consignee) dropdown and VESSEL-based purchase fetching
- **Purchase Form Enhancements**: Added Drive Type (LHD/RHD), improved Number Cut Information, button-based Options selection
- **Master Data Management**: Comprehensive master list section in sidebar for managing all reference data
- **Client Accounts Management**: Full client and transaction management with alerts and balance tracking
- **Real-time Calculations**: Automatic calculation of Total Cost (Before/After Tax) and Total Expense in C&F/FOB calculations
- **Project Cleanup**: Removed build artifacts and unnecessary files (~300MB saved)

## 🚀 Features

- **Modern Web UI**: Built with Kotlin JS Compose for a responsive and interactive interface
- **Multi-Platform Support**: Docker-based deployment works on Windows, macOS, and Linux
- **Database Integration**: MySQL database with automatic migrations and seeding
- **RESTful API**: Spring Boot backend with comprehensive CRUD operations
- **Search & Filter**: Real-time search and filtering capabilities with column filters and sorting
- **Car Booking System**: Complete workflow for booking cars, calculating C&F/FOB prices
- **Invoice Generation**: PDF invoice generation with dynamic client/vessel selection
- **Client Accounts Management**: Full client and transaction management with balance tracking, credit limits, and alerts
- **Master Data Management**: Comprehensive master list for clients, consignees, countries, suppliers, and more
- **User Management**: Admin and client account management with role-based access
- **Professional Design**: Clean, modern UI with Material Design principles
- **Real-time Calculations**: Automatic calculation of total costs, C&F/FOB prices, and expenses
- **Auto-fill Features**: Smart auto-fill from mapping tables for chassis, supplier, and booking information
- **Rixo Import**: CSV/text import for Rixo prices and mappings; Rixo PDF generation
- **Admin Approval Flow**: Signup requests require admin approval; Resend emails for verification

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Backend       │    │   Database      │
│   (Kotlin JS)   │◄──►│   (Spring Boot) │◄──►│   (MySQL)       │
│   Port: 8080    │    │   Port: 8083    │    │   Port: 3306    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              ▲
                              │
                       ┌──────┴──────┐
                       │ phpMyAdmin  │
                       │ Port: 8082  │
                       └─────────────┘
```

## 📋 Prerequisites

- **Docker Desktop**: Version 20.10 or higher
  - For Windows: Docker Desktop for Windows
  - For macOS: Docker Desktop for Mac
  - For Linux: Docker Engine and Docker Compose
- **Multi-platform Docker Images** (for `load-and-run-multiplatform.sh`): The `automan-complete-multiplatform.tar` file in the project root. If not present, build images from source (see Development section).

## 🛠️ Quick Start (Recommended)

### For macOS and Linux:

```bash
# From project root - make the script executable (first time only)
chmod +x scripts/run/load-and-run-multiplatform.sh

# Run the system (requires automan-complete-multiplatform.tar in project root)
./scripts/run/load-and-run-multiplatform.sh
```

The script will:
1. Check if Docker is running
2. Load the multi-platform Docker images
3. Start all services (MySQL, Backend, Frontend, phpMyAdmin)
4. Display access information

## 🌐 Access Points

After starting the system, access the application at:

- **Frontend Application**: http://localhost:8080
- **Backend API**: http://localhost:8083/api
- **phpMyAdmin**: http://localhost:8082
- **MySQL Database**: localhost:3306

## 🔑 Default Login Credentials

- **Email**: `admin@automan.com`
- **Password**: `admin123`

### Optional: Resend for signup approval emails

To enable admin approval emails (signup requests to admin, then approve/reject emails to users):

1. Create a [Resend](https://resend.com) account and create an API key at [API Keys](https://resend.com/api-keys). Use `onboarding@resend.dev` as sender for testing.
2. Copy `.env.example` to `.env` in the project root.
3. Set `RESEND_API_KEY=` your Resend API key. Optionally set `APP_FRONTEND_URL=` (e.g. `http://localhost:8080` or your app URL) for the "Sign In" link in approval emails.
4. From the project root, start (or restart) the stack:  
   `docker compose -f docker/docker-compose.multiplatform.yml up -d`

If `RESEND_API_KEY` is not set, the backend still runs; signup approval emails are simply skipped.

## 📊 Pre-populated Data

The system comes with sample data:
- 1 Admin user
- 1 Client account (Tokyo Auto Import)
- 4 Sample purchases
- Sample events and vessels
- Booking mappings for common countries

## 🗄️ Database Setup

The database is automatically initialized with:
- All required tables and columns
- Sample data for testing
- Booking mappings
- Car brand mappings
- Rixo price data

### Database Credentials

- **Host**: `localhost` (or `mysql` from within Docker network)
- **Port**: `3306`
- **Database**: `automan_car_purchase`
- **Username**: `automan_user`
- **Password**: `automan_password`
- **Root Password**: `rootpassword`

### Database Initialization

The `database/` directory contains a single consolidated SQL file:

- `01-init-multiplatform.sql` - Complete database initialization including:
  - All table definitions (users, clients, events, purchases, etc.)
  - **Essential seed data** with comments explaining why each INSERT is required:
    - `users` - Default admin account for initial login
    - `master_menu` - Form dropdown values (clients, countries, suppliers, etc.)
    - `car_brand_mapping` - Chassis code to vehicle details mapping
    - `booking_mappings` - Country/client to consignee/POD/POL mappings
    - `rixo_prices` - Auction house pricing data

**Note**: This file is run automatically on first database initialization.

## 🛠️ Manual Setup (Alternative)

If you prefer to set up manually or need to rebuild images:

### 1. Load Docker Images

```bash
# Load the multi-platform images
docker load -i automan-complete-multiplatform.tar
```

### 2. Start Services

```bash
# Start all services (from project root)
docker compose -f docker/docker-compose.multiplatform.yml up -d

# Check status
docker compose -f docker/docker-compose.multiplatform.yml ps
```

### 3. Verify Setup

```bash
# Run verification script
./scripts/verify-setup.sh
```

### Scripts Reference

| Script | Purpose |
|--------|---------|
| `scripts/run/load-and-run-multiplatform.sh` | Load Docker images and start all services (recommended) |
| `scripts/run/load-and-run.sh` | Alternative load script |
| `scripts/run/run-automan-multiplatform.sh` | Start services (assumes images already loaded) |
| `scripts/run/start-prod.sh` | Production start |
| `scripts/run/start-client.sh` | Client-only start |
| `scripts/build-and-deploy-frontend.sh` | Build and deploy frontend (version bump, Gradle, Docker) |
| `scripts/rebuild-and-restart-backend.sh` | Rebuild backend image and restart container |
| `scripts/restart-backend.sh` | Restart backend container (no rebuild) |
| `scripts/verify-setup.sh` | Verify Docker, DB, API, frontend |
| `scripts/verify-schema.sh` | Display database schema |
| `scripts/test-migrations.sh` | Test SQL migration syntax |

This script checks:
- Docker is running
- All containers are up
- Database connection
- Required tables exist
- Sample data is loaded
- API endpoints are responsive
- Frontend is accessible

## 🔧 Management Commands

### Stop the System

```bash
docker compose -f docker/docker-compose.multiplatform.yml down
```

### Restart Services

```bash
docker compose -f docker/docker-compose.multiplatform.yml restart
```

### View Logs

```bash
# All services
docker compose -f docker/docker-compose.multiplatform.yml logs

# Specific service
docker compose -f docker/docker-compose.multiplatform.yml logs backend
docker compose -f docker/docker-compose.multiplatform.yml logs frontend
docker compose -f docker/docker-compose.multiplatform.yml logs mysql
```

### Remove Everything (Including Data)

```bash
# Stop and remove containers, networks, and volumes
docker compose -f docker/docker-compose.multiplatform.yml down -v
```

### Clean Docker (Remove All Images and Containers)

**⚠️ Warning**: This removes ALL Docker containers and images on your system!

```bash
# For macOS/Linux
docker system prune -a --volumes
```

## 🔧 API Endpoints

All endpoints are prefixed with `/api` (backend context-path). Base URL: `http://localhost:8083/api`

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/signup` - User registration (admin approval flow)
- `GET /api/auth/check-email` - Check if email exists
- `GET /api/auth/pending-signups` - Get pending signup requests (admin)
- `GET /api/auth/verify-signup` - Verify signup token (email link)
- `GET /api/auth/users/count` - Get user count
- `POST /api/auth/setup` - Initial admin setup

### Users
- `GET /api/users` - Get all users
- `GET /api/users/{id}` - Get user by ID
- `POST /api/users` - Create user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user
- `POST /api/users/{userId}/role-request` - Request role change

### Role Requests
- `POST /api/role-requests/{userId}` - Create role request
- `GET /api/role-requests/user/{userId}` - Get requests by user
- `GET /api/role-requests/pending` - Get pending requests
- `POST /api/role-requests/{requestId}/review/{reviewerId}` - Review (approve/reject)

### Purchases
- `GET /api/purchases` - Get all purchases
- `GET /api/purchases/purchase/{id}` - Get purchase by ID
- `POST /api/purchases` - Create new purchase
- `PUT /api/purchases/{id}` - Update purchase
- `PATCH /api/purchases/{id}` - Partial update purchase
- `DELETE /api/purchases/{id}` - Delete purchase
- `GET /api/purchases/search?query={term}` - Search purchases
- `GET /api/purchases/unshipped-chassis?pol={port}` - Get unshipped chassis
- `GET /api/purchases/filtered-chassis?country={country}&polPort={pol}` - Get filtered unshipped chassis
- `GET /api/purchases/filter/invoice?consignee={client}&vessel={vessel}&shipmentDate={date}` - Filter purchases for invoice
- `POST /api/purchases/ship` - Mark purchases as shipped
- `POST /api/purchases/save-total-cnf` - Save total C&F price
- `POST /api/purchases/save-total-cnf-by-ids` - Save total C&F price by purchase IDs
- `GET /api/purchases/costs-by-chassis/{chassis}` - Get cost details by chassis
- `GET /api/purchases/countries` - Get list of countries
- `GET /api/purchases/stock-locations` - Get stock locations
- `POST /api/purchases/invoice/generate-pdf` - Generate invoice PDF
- `POST /api/purchases/shipping-schedule/generate-pdf` - Generate shipping schedule PDF
- `POST /api/purchases/fob-shipping-schedule/generate-pdf` - Generate FOB shipping schedule PDF
- `POST /api/purchases/rixo-pdf` - Generate Rixo PDF
- `POST /api/purchases/rixo-transport-pdf` - Generate Rixo transport PDF
- `POST /api/purchases/import` - Import purchases
- `GET /api/purchases/filter/car-name`, `/filter/auction-house`, `/filter/client-name`, `/filter/date` - Filter purchases
- `POST /api/purchases/transaction` - Add transaction to purchase

### Booking Mappings
- `GET /api/booking/mappings` - Get all booking mappings
- `GET /api/booking/mappings/by-country/{country}` - Get mappings by country
- `POST /api/booking/mappings/add` - Create booking mapping
- `PUT /api/booking/mappings/{id}` - Update booking mapping
- `DELETE /api/booking/mappings/{id}` - Delete booking mapping

### Car Brand Mapping
- `GET /api/car-brand-mapping/mappings` - Get all car brand mappings
- `GET /api/car-brand-mapping/brand/{brandName}` - Get mappings by brand
- `GET /api/car-brand-mapping/chassis/{chassis}` - Get mapping by chassis
- `GET /api/car-brand-mapping/chassis/all` - Get all chassis mappings
- `POST /api/car-brand-mapping/mappings` - Create mapping
- `PUT /api/car-brand-mapping/mappings/{id}` - Update mapping
- `DELETE /api/car-brand-mapping/mappings/{id}` - Delete mapping

### Rixo Import & Prices
- `GET /api/rixo/prices` - Get Rixo prices
- `GET /api/rixo/prices/by-auction-house/{auctionHouse}` - Get prices by auction house
- `GET /api/rixo/dropdowns/auction-names`, `/dropdowns/stock-locations`, `/dropdowns/rixo-companies`, `/dropdowns/rixo-prices` - Dropdown data
- `POST /api/rixo/import/csv` - Import from CSV
- `POST /api/rixo/import/text` - Import from text
- `GET /api/rixo/mappings/by-auction/{auctionHouse}` - Get mappings by auction
- `POST /api/rixo/mappings/add` - Add Rixo mapping
- `PUT /api/rixo/mappings/{id}` - Update Rixo mapping
- `DELETE /api/rixo/mappings/{id}` - Delete Rixo mapping

### Calculations (C&F/FOB)
- `POST /api/calculations/freight` - Calculate freight
- `POST /api/calculations/caf` - Calculate C&F
- `POST /api/calculations/fob` - Calculate FOB
- `POST /api/calculations/pakistan` - Pakistan-specific calculation

### Car Search & Booking
- `GET /api/cars/search` - Search cars
- `GET /api/cars/booking/{bookingId}` - Get booking by ID
- `POST /api/booking-cars` - Create booking
- `DELETE /api/booking-cars/{bookingId}` - Delete booking

### File Upload
- `POST /api/upload/excel` - Upload Excel file
- `POST /api/upload/simple` - Simple file upload

### Transactions
- `POST /api/transactions` - Create transaction

### Events (Client Transactions)
- `GET /api/events` - Get events
- `GET /api/events/client/{clientId}` - Get events by client
- `POST /api/events` - Create event
- `GET /api/events/export/{clientId}` - Export client events
- `GET /api/events/export/all-clients` - Export all clients' events

### Clients
- `GET /api/clients` - Get all clients
- `GET /api/clients/{id}` - Get client by ID
- `POST /api/clients` - Create client
- `PUT /api/clients/{id}` - Update client
- `DELETE /api/clients/{id}` - Delete client
- `GET /api/clients/{id}/transactions` - Get client transactions
- `GET /api/clients/alerts` - Get client alerts (credit limit, low balance)
- `POST /api/clients/{id}/transactions` - Add transaction to client
- `POST /api/clients/add-transaction` - Add transaction (alternate)
- `POST /api/clients/import` - Import clients

## 🎯 Usage Guide

### Car Booking Workflow

1. **Navigate to Car Booking Page**
   - Select a country from the dropdown
   - Select a POL (Port of Loading)
   - Enter chassis number or select from dropdown
   - Select cars to book

2. **Fill Booking Details**
   - ETD Date
   - Booking Number
   - Vessel Name
   - POD (Port of Discharge)
   - Consignee information (auto-filled from mappings)

3. **Calculate Prices**
   - Click "Calculate" to proceed to C&F/FOB calculation
   - Enter freight values if needed
   - System calculates total C&F/FOB prices

4. **Generate Invoice**
   - Navigate to Invoice page via sidebar
   - Select CLIENT (consignee) from dropdown
   - Enter VESSEL name
   - System automatically fetches matching purchases and fills shipping details
   - Generate PDF invoice with all details
   - Mark cars as shipped after invoice generation

### Adding a New Purchase

1. Navigate to "Add New Purchase" page
2. Fill in all required fields:
   - **Basic Information**: Auction No, Grade (text input), Shaken checkbox
   - **Number Cut Information**: Visible when Shaken is checked (Place Name, Number, Hiragana)
   - **Car Specifications**: Rank, Color, Mileage, Transmission, Drive Type (LHD/RHD), Seat (with spinner), Door (with spinner), Options (button-based selection)
   - **Supplier Information**: Supplier Name, Venue ID
   - **Rixo Information**: Rixo Company, Stock Location, Rixo Price, Rixo Requested, Rixo Confirmed
   - **Pricing Information**: Car Price, Auction Fees, Recycle Fees, Road Tax, Payment Date
   - **Shipment Information**: Client Name, Target Country, Vessel, Shipping Date, FROM/TO locations, charges, and totals (auto-calculated)
3. System automatically calculates:
   - Total Cost (Before Tax) - updates in real-time
   - Total Cost (After Tax 10%) - updates after tax calculation
4. Click "Add Purchase" to save

### Managing Users

1. Navigate to "User Management" page (Admin only)
2. View all users
3. Add, edit, or delete users
4. Manage user roles and permissions

### Managing Clients

1. Navigate to "Master List → Clients" page
2. View all client accounts with balance and credit limit information
3. Add new clients with client number, name, address, credit limit, and alert threshold
4. Edit client information and delete clients
5. View client transaction history and running balance
6. Add transactions (payments, shipments, adjustments)
7. View client alerts (credit limit warnings, low balance)

### Master Data Management

Access master data via the sidebar "Master" section:
- **Clients**: Manage client master data with balance tracking
- **Consignee**: Manage consignee information with country-based filtering
- **Car Brands**: Manage car brand mappings with search and pagination
- **Suppliers**: Manage supplier information (add/edit/delete/duplicate, column filters, pagination, auto-refresh in purchase forms)
- **Countries**: Manage country data (coming soon)
- **Rixo Companies**: Manage Rixo company data (coming soon)
- **Stock Locations**: Manage stock locations (coming soon)
- **Repair Companies**: Manage repair company data (coming soon)
- **Bank Accounts**: Manage bank account information (coming soon)
- **Venue IDs**: Manage venue IDs for Rixo pricing (coming soon)

### Reports & Analytics (Coming Soon)

The following report pages are planned:
- **Shipment Status**: Track shipping status of purchases with filters by status, country, date range, and vessel
- **Sales Report**: Sales analytics with date range filtering, client grouping, and profit calculations
- **Stock Report**: Inventory management with stock location filtering and days-in-stock tracking
- **Purchase Report**: Purchase history and analysis with supplier grouping and cost breakdowns

### Invoice Management

1. Click "Invoice" button in the sidebar
2. Select CLIENT (consignee) from the dropdown (populated from purchases table)
3. Enter VESSEL name
4. System automatically:
   - Fetches all purchases matching the selected CLIENT and VESSEL
   - Auto-fills Shipping Date, FROM (stockLocation), TO (destination)
   - Populates the LIST table with matching cars
5. Fill in invoice details (Invoice Number, LC No., Price Type, Bank Account, Message)
6. Generate Invoice Number or enter manually
7. Download PDF, Email, Export Excel, or Ship Cars

## 🐛 Troubleshooting

### Docker Issues

**Problem**: Docker is not running
- **Solution**: Start Docker Desktop and wait for it to fully start

**Problem**: Port already in use
- **Solution**: Stop the service using the port or change the port in `docker/docker-compose.multiplatform.yml`

**Problem**: Images not found
- **Solution**: Ensure `automan-complete-multiplatform.tar` is in the project root and run:
  ```bash
  docker load -i automan-complete-multiplatform.tar
  ```

### Database Connection Issues

**Problem**: Cannot connect to database
- **Solution**: 
  1. Check if MySQL container is running: `docker ps | grep mysql`
  2. Check MySQL logs: `docker compose -f docker/docker-compose.multiplatform.yml logs mysql`
  3. Wait a few seconds after starting - MySQL needs time to initialize

**Problem**: Database migrations not running
- **Solution**:
  1. **Incremental schema changes** ship as **Flyway** scripts under `backend/src/main/resources/db/migration/` (`V1__….sql`, `V15__….sql`, `V16__….sql`, etc.). They run **automatically when the Spring Boot backend starts** (`spring.flyway.enabled: true` in `application.yml`). Deploy a new backend image/JAR that includes the new files, ensure MySQL is up and the datasource URL points at the right database, then **restart the backend**; Flyway applies **pending** migrations in version order and records them in `flyway_schema_history`.
  2. The files under `database/` (e.g. `01-init-multiplatform.sql`) are mainly for **initial Docker/MySQL seeding** of an empty volume — they are not a substitute for Flyway on an existing environment.
  3. If startup fails after a migration, check **backend logs** and the `flyway_schema_history` table. Docker-oriented setups may use `FlywayDockerRepairConfig`, which runs `repair()` then `migrate()` on startup.

### Flyway on deploy (V15, V16, …)

No separate “flyway deploy” step is required: **migrations run as part of backend startup.** After you merge migrations such as `V15__purchases_drop_vessel_no.sql` and `V16__purchases_search_indexes.sql`, build and deploy the backend, then restart it against the target MySQL instance. Flyway will apply V15 before V16 whenever both are pending.

- **Profiles**: `application-docker.yml` and `application-prod.yml` set `baseline-on-migrate: true` (and baseline version `1`) when the database already contains objects from an older init path — adjust only if you know your `flyway_schema_history` state.
- **Verify**: `SELECT * FROM flyway_schema_history ORDER BY installed_rank;` in MySQL, or watch backend logs for `Flyway` / migration success lines.

### Backend Issues

**Problem**: Backend not responding
- **Solution**:
  1. Check backend logs: `docker compose -f docker/docker-compose.multiplatform.yml logs backend`
  2. Verify backend container is running: `docker ps | grep backend`
  3. Check if port 8083 is available

**Problem**: API returns 500 errors
- **Solution**:
  1. Check backend logs for detailed error messages
  2. Verify database connection
  3. Check if all required tables exist

### Frontend Issues

**Problem**: Frontend not loading (ERR_CONNECTION_REFUSED on localhost:8080)
- **Solution**:
  1. Verify frontend container is running: `docker ps | grep frontend`
  2. Use `./scripts/build-and-deploy-frontend.sh` for deployment (it uses compose so the frontend joins the same network as the backend; nginx needs to resolve the backend host)
  3. Check frontend logs: `docker compose -f docker/docker-compose.multiplatform.yml logs frontend`
  4. Clear browser cache and hard refresh (Cmd+Shift+R / Ctrl+Shift+R)

**Problem**: Changes not appearing
- **Solution**: The frontend uses cache busting. If changes don't appear:
  1. Run the deploy script (recommended): `./scripts/build-and-deploy-frontend.sh`
  2. Hard refresh the page (Ctrl+Shift+R or Cmd+Shift+R)
  3. Clear browser cache if needed

### phpMyAdmin Issues

**Problem**: Cannot access phpMyAdmin
- **Solution**:
  1. Verify container is running: `docker ps | grep phpmyadmin`
  2. Access at http://localhost:8082
  3. Use credentials: `automan_user` / `automan_password`

## 📁 Project Structure

```
Automan2.0/
├── backend/                          # Spring Boot Backend (separate Gradle project)
│   ├── Dockerfile                   # Backend Dockerfile (builds from source)
│   ├── Dockerfile.prebuilt          # Prebuilt backend (faster builds)
│   ├── build.gradle.kts             # Backend build config
│   ├── src/main/kotlin/com/automan/backend/
│   │   ├── BackendApplication.kt
│   │   ├── controller/               # Auth, Purchase, Client, User, CarBrandMapping,
│   │   │                             # BookingMapping, RixoImport, Calculation, etc.
│   │   ├── model/                    # Purchase, Client, User, Event, PendingSignup, etc.
│   │   ├── repository/               # JPA repositories
│   │   ├── service/                  # Auth, Purchase, Pdf, Email, RixoImport, etc.
│   │   ├── dto/                      # Request/response DTOs
│   │   ├── config/                   # WebConfig, WebCorsConfig, AppConstants
│   │   ├── exception/                # GlobalExceptionHandler
│   │   └── util/                     # Logger
│   ├── src/main/resources/
│   │   ├── application.yml           # Main config
│   │   ├── application-docker.yml   # Docker profile
│   │   └── application-dev.yml     # Dev profile
│   └── src/test/                    # Integration tests
├── src/jsMain/                       # Kotlin JS Frontend
│   ├── kotlin/com/automan/purchase/
│   │   ├── MinimalPurchaseApp.kt    # Main Application & Routing
│   │   ├── PurchaseManagement.kt   # Purchase list & management
│   │   ├── ClientManagement.kt     # Client accounts & transactions
│   │   ├── CarBooking.kt           # Car booking system
│   │   ├── Invoice.kt              # Invoice generation
│   │   ├── MasterList.kt           # Master data lists (Car Brands, Suppliers, etc.)
│   │   ├── CnfFobCalculation.kt   # C&F/FOB price calculations
│   │   ├── FreightCalculation.kt   # Freight calculations
│   │   ├── ApiService.kt           # API client
│   │   ├── ApiClient.kt            # HTTP client utilities
│   │   ├── Models.kt               # Data models
│   │   ├── Utils.kt                # Date formatting, HTML escaping
│   │   ├── AppConstants.kt         # Centralized constants
│   │   ├── Logger.kt               # Logging utility
│   │   └── AuthSetup.kt           # Authentication & user setup
│   └── resources/
│       ├── index.html                # HTML Template
│       ├── styles.css                # Global styles
│       ├── rixo-price-mapping.js     # Rixo price mapping logic
│       ├── booking-mapping.js        # Booking mapping logic
│       └── booking-mapping-modal.js  # Booking modal logic
├── database/                         # MySQL initialization (run on first DB start)
│   └── 01-init-multiplatform.sql    # Complete schema + essential seed data
├── docker/
│   ├── docker-compose.multiplatform.yml  # Main compose (MySQL, backend, frontend, phpMyAdmin)
│   ├── docker-compose.prod.yml.example   # Production compose example
│   ├── docker-compose.hub.yml.example    # Docker Hub compose example
│   ├── Dockerfile.frontend.prod     # Frontend: nginx + built assets
│   ├── Dockerfile.multiplatform    # Multi-platform build
│   ├── Dockerfile                   # Legacy single Dockerfile
│   ├── Dockerfile.client            # Client-only build
│   ├── supervisord.conf             # Process manager config
│   └── nginx/
│       ├── nginx-prod.conf          # Production (proxies /api to backend)
│       ├── nginx.conf               # Default config
│       ├── nginx-single.conf        # Single-container config
│       └── nginx-client.conf        # Client-only config
├── scripts/
│   ├── run/
│   │   ├── load-and-run-multiplatform.sh   # Load images + start (recommended)
│   │   ├── load-and-run.sh                 # Alternative load script
│   │   ├── run-automan-multiplatform.sh    # Run without loading
│   │   ├── start-prod.sh                   # Production start
│   │   ├── start-client.sh                 # Client-only start
│   │   └── seed_booking_mappings.py        # Seed booking mappings
│   ├── build-and-deploy-frontend.sh # Frontend: bump version, build, deploy
│   ├── rebuild-and-restart-backend.sh # Backend: rebuild image, restart
│   ├── restart-backend.sh           # Restart backend (no rebuild)
│   ├── verify-setup.sh              # Verify containers, DB, API, frontend
│   ├── verify-schema.sh             # Show DB schema
│   ├── test-migrations.sh           # Test SQL migrations
│   └── update_venue_ids_from_csv.py # Update venue IDs from CSV
├── webpack.config.d/                # Webpack config for Kotlin/JS
├── docs/                             # Page documentation
│   ├── ADD_PURCHASE_PAGE_DOCUMENTATION.md
│   ├── EDIT_PURCHASE_PAGE_DOCUMENTATION.md
│   ├── CAR_BOOKING_SYSTEM_DOCUMENTATION.md
│   ├── CLIENT_ACCOUNTS_AND_TRANSACTION_PAGE_DOCUMENTATION.md
│   └── INVOICE_PAGE_DOCUMENTATION.md
├── .env.example                     # Env template (Resend, etc.)
├── .dockerignore
├── .gitignore
├── build.gradle.kts                 # Root build (Kotlin/JS)
├── settings.gradle.kts
├── gradle.properties
├── copy-kotlin-dependencies.sh      # Copy Kotlin deps utility
└── README.md
```

## 🚀 Development

### Building from Source

If you need to rebuild the Docker images:

```bash
# Backend (from project root)
docker build -t automan20-backend:latest -f backend/Dockerfile backend/

# Frontend (build Kotlin/JS first, then Docker)
./gradlew jsBrowserProductionWebpack
docker build -t automan20-frontend:latest -f docker/Dockerfile.frontend.prod .

# Or use the convenience scripts:
./scripts/rebuild-and-restart-backend.sh
./scripts/build-and-deploy-frontend.sh
```

For multi-platform builds (e.g. ARM64 for AWS Graviton):
```bash
docker buildx build --platform linux/amd64,linux/arm64 -t automan20-backend:latest -f backend/Dockerfile backend/
docker buildx build --platform linux/amd64,linux/arm64 -t automan20-frontend:latest -f docker/Dockerfile.frontend.prod .
```

### Quick Frontend Rebuild and Deploy

For quick frontend updates (after code changes):

**macOS/Linux:**
```bash
# Build and deploy frontend (bumps version, builds Kotlin/JS, rebuilds Docker image, recreates container)
./scripts/build-and-deploy-frontend.sh
```

This script: bumps cache-bust version in `index.html`, runs `./gradlew jsBrowserProductionWebpack`, builds the frontend Docker image, and recreates the frontend container via compose (so it joins the same network as the backend for nginx proxy).

### Quick Backend Rebuild and Restart

```bash
# Rebuild backend image and restart container
./scripts/rebuild-and-restart-backend.sh
```

### Running Backend Locally

The backend is a separate Gradle project. Ensure MySQL is running (or use Docker for MySQL only).

```bash
cd backend
./gradlew bootRun
```

Backend will use `application.yml` (localhost:3306). For Docker profile: `SPRING_PROFILES_ACTIVE=docker ./gradlew bootRun`

### Running Frontend Locally

```bash
./gradlew jsBrowserDevelopmentRun
```

## 📝 Notes

- **Docker Volumes**: The system uses Docker volumes to persist database data. Data is retained even after stopping containers. To start fresh, use `docker compose -f docker/docker-compose.multiplatform.yml down -v` to remove volumes.
- **Database Initialization**: MySQL runs `database/01-init-multiplatform.sql` on first init (empty volume). The script creates all tables and inserts essential seed data.
- **Frontend Cache Busting**: The frontend uses cache busting. Use `./scripts/build-and-deploy-frontend.sh` to bump version, build, and deploy. The script updates `index.html`, builds the Kotlin/JS bundle, rebuilds the Docker image, and recreates the container.
- **Database Schema**: 
  - Main tables: `purchases`, `clients`, `events`, `users`, `pending_signups`
  - Mapping tables: `car_brand_mapping`, `booking_mappings`, `rixo_prices`
  - Key columns: `drive_type` (VARCHAR(50) NULL), `booking_id` (BIGINT NULL, no FK constraint), `total_fob_price` (DECIMAL(15,2) NULL), `shipped` (BOOLEAN DEFAULT FALSE), `vessel` (VARCHAR(255) NULL)
- **Invoice Page**: Uses CLIENT (consignee) dropdown and VESSEL input to dynamically fetch matching purchases by shipment date.
- **Client Accounts**: Full client management with balance tracking, credit limits, alerts, and transaction history.
- **Security**: 
  - All user input is HTML-escaped to prevent XSS attacks
  - localStorage operations are wrapped with error handling
  - Logger utilities allow conditional logging (can be disabled in production)
- **Code Quality**: 
  - Constants centralized in `AppConstants` (frontend: `src/jsMain/kotlin/com/automan/purchase/AppConstants.kt`, backend: `backend/src/main/kotlin/com/automan/backend/config/AppConstants.kt`)
  - Logger utilities for consistent logging (frontend: `Logger.kt`, backend: `backend/src/main/kotlin/com/automan/backend/util/Logger.kt`)
  - All critical and high-priority bugs have been fixed
- **Project Cleanup**: Build artifacts and unnecessary files have been removed. Run `./gradlew build` to regenerate build files.

## 🔒 Security Features

- **XSS Protection**: All user input is HTML-escaped before rendering
- **Safe localStorage**: Wrapped with error handling for quota exceeded and disabled storage scenarios
- **Input Validation**: Comprehensive validation on all form inputs
- **Error Handling**: Proper error handling with logging throughout the application
- **CORS Configuration**: Properly configured CORS for API security

## 🧪 Code Quality

- **Centralized Constants**: All hardcoded values moved to `AppConstants` for easy maintenance
- **Logger Utilities**: Conditional logging that can be disabled in production
- **Type Safety**: Improved null safety and array bounds checking
- **Memory Management**: Fixed memory leaks (event listeners, blob URLs)
- **Race Condition Fixes**: Proper Promise.all handling for concurrent operations

## 🗺️ Planned Features

The following pages are planned for future implementation:

### Master Lists
- **Country Master**: Manage target countries with statistics
- **Rixo Company Master**: Manage Rixo companies with usage statistics
- **Stock Location Master**: Manage stock locations with current stock counts
- **Repair Company Master**: Manage repair companies with repair statistics
- **Bank Accounts Master**: Manage bank accounts with masked sensitive data
- **Venue IDs Master**: Manage venue IDs with relationships to stock locations and Rixo companies

### Reports
- **Shipment Status**: Track shipping status with filters and bulk actions
- **Sales Report**: Sales analytics with grouping, charts, and export
- **Stock Report**: Inventory management with alerts for long-staying cars
- **Purchase Report**: Purchase history with supplier grouping and cost analysis

All new pages will follow the existing pattern:
- Pagination (20 items per page)
- Search and filtering
- Sortable columns
- Add/Edit/Delete functionality
- CSV export
- Responsive design

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Follow code quality guidelines (use Logger, AppConstants, escape HTML)
6. Submit a pull request



---

**Happy coding! 🚗💼✨**
