# Automan Car Purchase Management System

A comprehensive car purchase management system built with Kotlin JS Compose for the frontend and Spring Boot for the backend, with MySQL database integration. The system is containerized using Docker for easy deployment across multiple platforms (Windows, macOS, Linux).

## 🆕 Recent Updates

### Latest Changes (January 2026)
- **Security Improvements**: 
  - XSS protection with HTML escaping for all user-generated content
  - Safe localStorage wrappers with error handling
  - Improved error logging and handling
- **Code Quality Enhancements**:
  - Centralized constants in `AppConstants` (frontend and backend)
  - Logger utilities for conditional logging (replacing console.log/println)
  - Fixed race conditions in concurrent purchase updates
  - Improved Promise.all error handling
- **Bug Fixes**: 
  - Fixed memory leaks (event listeners, URL.createObjectURL)
  - Fixed race conditions in form population
  - Improved date parsing edge cases
  - Enhanced null safety and array bounds checking
- **Sidebar Navigation Reorganization**: 
  - Renamed "Purchase List" to "Home"
  - Renamed "Rixo Request" to "Rixo Note"
  - Added new sections: Shipment, Sales, Reports
  - Organized navigation with collapsible sections

### Previous Updates
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
- **Multi-platform Docker Images**: The `automan-complete-multiplatform.tar` file (included in the project)

## 🛠️ Quick Start (Recommended)

### For macOS and Linux:

```bash
# Make the script executable (first time only)
chmod +x scripts/run/load-and-run-multiplatform.sh

# Run the system
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

### Database Migrations

All SQL migration files in the `database/` directory are automatically executed in order:

**Main Migrations:**
- `01-init-multiplatform.sql` - Main schema (purchases table and related indexes)
- `02-car-brand-mapping.sql` - Car brand mappings table and seed data
- `03-booking-mappings.sql` - Booking country mappings table and seed data
- `04-rixo-prices.sql` - Rixo prices table, data import, venue ID updates, and currency cleanup (fully consolidated)

**Client & User Management:**
- `10-clients-table.sql` - Clients table with indexes and sample data
- `11-events-table.sql` - Events table for client transactions
- `12-users-table.sql` - Users table for authentication

**Note**: All migrations are idempotent and can be run multiple times safely. The `04-rixo-prices.sql` file includes all consolidated rixo-related migrations (data import, venue updates, currency cleanup).

## 🛠️ Manual Setup (Alternative)

If you prefer to set up manually or need to rebuild images:

### 1. Load Docker Images

```bash
# Load the multi-platform images
docker load -i automan-complete-multiplatform.tar
```

### 2. Start Services

```bash
# Start all services
docker-compose -f docker/docker-compose.multiplatform.yml up -d

# Check status
docker-compose -f docker/docker-compose.multiplatform.yml ps
```

### 3. Verify Setup

```bash
# Run verification script
./scripts/verify-setup.sh
```

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
docker-compose -f docker/docker-compose.multiplatform.yml down
```

### Restart Services

```bash
docker-compose -f docker/docker-compose.multiplatform.yml restart
```

### View Logs

```bash
# All services
docker-compose -f docker/docker-compose.multiplatform.yml logs

# Specific service
docker-compose -f docker/docker-compose.multiplatform.yml logs backend
docker-compose -f docker/docker-compose.multiplatform.yml logs frontend
docker-compose -f docker/docker-compose.multiplatform.yml logs mysql
```

### Remove Everything (Including Data)

```bash
# Stop and remove containers, networks, and volumes
docker-compose -f docker/docker-compose.multiplatform.yml down -v
```

### Clean Docker (Remove All Images and Containers)

**⚠️ Warning**: This removes ALL Docker containers and images on your system!

```bash
# For macOS/Linux
docker system prune -a --volumes
```

## 🔧 API Endpoints

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/register` - User registration
- `GET /api/auth/users/count` - Get user count

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

### Booking
- `GET /api/booking-mappings` - Get booking mappings
- `GET /api/booking-mappings/country/{country}` - Get mappings by country
- `POST /api/booking-mappings` - Create booking mapping
- `PUT /api/booking-mappings/{id}` - Update booking mapping
- `DELETE /api/booking-mappings/{id}` - Delete booking mapping

### Clients
- `GET /api/clients` - Get all clients
- `GET /api/clients/{id}` - Get client by ID
- `POST /api/clients` - Create client
- `PUT /api/clients/{id}` - Update client
- `DELETE /api/clients/{id}` - Delete client
- `GET /api/clients/{id}/transactions` - Get client transactions
- `GET /api/clients/alerts` - Get client alerts (credit limit, low balance)
- `POST /api/clients/{id}/transactions` - Add transaction to client

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
- **Suppliers**: Manage supplier information (coming soon)
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
  2. Check MySQL logs: `docker-compose -f docker/docker-compose.multiplatform.yml logs mysql`
  3. Wait a few seconds after starting - MySQL needs time to initialize

**Problem**: Database migrations not running
- **Solution**: 
  1. Check that SQL files are in `database/` directory
  2. Ensure files are named with numeric prefixes (e.g., `01-init-multiplatform.sql`)
  3. Check MySQL logs for migration errors

### Backend Issues

**Problem**: Backend not responding
- **Solution**:
  1. Check backend logs: `docker-compose -f docker/docker-compose.multiplatform.yml logs backend`
  2. Verify backend container is running: `docker ps | grep backend`
  3. Check if port 8083 is available

**Problem**: API returns 500 errors
- **Solution**:
  1. Check backend logs for detailed error messages
  2. Verify database connection
  3. Check if all required tables exist

### Frontend Issues

**Problem**: Frontend not loading
- **Solution**:
  1. Check frontend logs: `docker-compose -f docker/docker-compose.multiplatform.yml logs frontend`
  2. Clear browser cache (Ctrl+Shift+Delete or Cmd+Shift+Delete)
  3. Try accessing in incognito/private mode
  4. Check browser console for JavaScript errors

**Problem**: Changes not appearing
- **Solution**: The frontend uses cache busting. If changes don't appear:
  1. Rebuild frontend: `./gradlew compileKotlinJs`
  2. Update version in `src/jsMain/resources/index.html`
  3. Copy files to container:
     ```bash
     docker cp src/jsMain/resources/index.html automan_frontend_multiplatform:/usr/share/nginx/html/
     docker cp build/dist/js/productionExecutable/automan-car-purchase.js automan_frontend_multiplatform:/usr/share/nginx/html/
     docker exec automan_frontend_multiplatform nginx -s reload
     ```
  4. Hard refresh the page (Ctrl+F5 or Cmd+Shift+R)
  5. Clear browser cache

### phpMyAdmin Issues

**Problem**: Cannot access phpMyAdmin
- **Solution**:
  1. Verify container is running: `docker ps | grep phpmyadmin`
  2. Access at http://localhost:8082
  3. Use credentials: `automan_user` / `automan_password`

## 📁 Project Structure

```
Automan2.0/
├── backend/                          # Spring Boot Backend
│   ├── src/main/kotlin/
│   │   └── com/automan/backend/
│   │       ├── BackendApplication.kt
│   │       ├── controller/           # REST Controllers
│   │       ├── model/                # Data Models (Purchase, Client, etc.)
│   │       ├── repository/           # Data Repositories (JPA)
│   │       ├── service/              # Business Logic
│   │       ├── config/              # Configuration (CORS, AppConstants)
│   │       └── util/                 # Utilities (Logger)
│   └── src/main/resources/
│       └── application.yml           # Configuration
├── src/jsMain/                       # Kotlin JS Frontend
│   ├── kotlin/com/automan/purchase/
│   │   ├── MinimalPurchaseApp.kt    # Main Application & Routing
│   │   ├── PurchaseManagement.kt   # Purchase list & management
│   │   ├── ClientManagement.kt     # Client accounts & transactions
│   │   ├── CarBooking.kt           # Car booking system
│   │   ├── Invoice.kt              # Invoice generation
│   │   ├── MasterList.kt           # Master data lists (Car Brands, Suppliers, etc.)
│   │   ├── CnfFobCalculation.kt   # C&F/FOB price calculations
│   │   ├── Utils.kt                # Utility functions (date formatting, HTML escaping)
│   │   ├── AppConstants.kt         # Centralized constants
│   │   ├── Logger.kt                # Logging utility
│   │   └── AuthSetup.kt            # Authentication & user setup
│   └── resources/
│       ├── index.html                # HTML Template
│       ├── styles.css                # Global styles
│       ├── rixo-price-mapping.js     # Rixo price mapping logic
│       ├── booking-mapping.js        # Booking mapping logic
│       └── booking-mapping-modal.js  # Booking modal logic
├── database/                         # Database Migrations
│   ├── 01-init-multiplatform.sql    # Main schema (purchases, bookings, vessels)
│   ├── 02-car-brand-mapping.sql     # Car brand mappings
│   ├── 03-booking-mappings.sql      # Booking country mappings
│   ├── 04-rixo-prices.sql          # Rixo prices (fully consolidated)
│   ├── 10-clients-table.sql         # Clients table
│   ├── 11-events-table.sql          # Events table
│   ├── 12-users-table.sql          # Users table
│   ├── init.sql                     # Legacy init file
│   └── archived/                    # Archived migrations (reference only)
├── docker/                          # Docker Configuration
│   ├── docker-compose.multiplatform.yml  # Main compose file
│   ├── Dockerfile                    # Frontend Dockerfile
│   ├── Dockerfile.multiplatform     # Multi-platform build
│   └── nginx/                       # Nginx Configuration
│       └── nginx.conf
├── scripts/                         # Utility Scripts
│   ├── run/                         # Run Scripts
│   │   └── run-automan-multiplatform.sh
│   ├── build/                       # Build Scripts
│   ├── sql/                         # SQL Scripts
│   │   └── archived/                # Archived SQL (reference only)
│   ├── test-migrations.sh           # Migration testing
│   ├── verify-schema.sh             # Schema verification
│   └── verify-setup.sh              # Setup verification
├── docs/                            # Documentation
│   ├── ADD_PURCHASE_PAGE_DOCUMENTATION.md
│   ├── EDIT_PURCHASE_PAGE_DOCUMENTATION.md
│   ├── CAR_BOOKING_SYSTEM_DOCUMENTATION.md
│   ├── CLIENT_ACCOUNTS_AND_TRANSACTION_PAGE_DOCUMENTATION.md
│   └── INVOICE_PAGE_DOCUMENTATION.md
├── build.gradle.kts                 # Build Configuration
├── settings.gradle.kts              # Project Settings
├── gradle.properties                # Gradle Properties
└── README.md                        # This File
```

## 🚀 Development

### Building from Source

If you need to rebuild the Docker images:

```bash
# Build multi-platform images
./scripts/build/build-multiplatform.sh

# Or manually
docker buildx build --platform linux/amd64,linux/arm64 -t automan20-backend:latest -f backend/Dockerfile backend/
docker buildx build --platform linux/amd64,linux/arm64 -t automan20-frontend:latest -f docker/Dockerfile.multiplatform .
```

### Quick Frontend Rebuild and Deploy

For quick frontend updates (after code changes):

**macOS/Linux:**
```bash
# Build and deploy frontend
cd docker
docker-compose -f docker-compose.multiplatform.yml build frontend
docker-compose -f docker-compose.multiplatform.yml up -d --force-recreate frontend
```

Or use the build scripts in `scripts/build/` directory.

### Running Backend Locally

```bash
cd backend
./gradlew bootRun
```

### Running Frontend Locally

```bash
./gradlew jsBrowserDevelopmentRun
```

## 📝 Notes

- **Docker Volumes**: The system uses Docker volumes to persist database data. Data is retained even after stopping containers. To start fresh, use `docker-compose down -v` to remove volumes.
- **Database Migrations**: All migrations run automatically on first startup in alphabetical order. Migrations are idempotent and safe to run multiple times.
- **Frontend Cache Busting**: The frontend uses cache busting - update the version in `src/jsMain/resources/index.html` when deploying new changes. After updating, copy files to container and reload nginx:
  ```bash
  docker cp src/jsMain/resources/index.html automan_frontend_multiplatform:/usr/share/nginx/html/
  docker cp build/dist/js/productionExecutable/automan-car-purchase.js automan_frontend_multiplatform:/usr/share/nginx/html/
  docker exec automan_frontend_multiplatform nginx -s reload
  ```
- **Database Schema**: 
  - Main tables: `purchases`, `clients`, `events`, `users`, `bookings`, `vessels`, `booking_calculations`
  - Mapping tables: `car_brand_mapping`, `booking_mappings`, `rixo_prices`
  - Key columns: `drive_type` (VARCHAR(50) NULL), `booking_id` (BIGINT NULL, no FK constraint), `total_fob_price` (DECIMAL(15,2) NULL), `shipped` (BOOLEAN DEFAULT FALSE)
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
- **Supplier Master**: Manage suppliers/auction houses with purchase statistics
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

## 📄 License

This project is licensed under the MIT License.

---

**Happy coding! 🚗💼✨**
