# Automan Car Purchase Management System

A comprehensive car purchase management system built with Kotlin JS Compose for the frontend and Spring Boot for the backend, with MySQL database integration.

## 🚀 Features

- **Modern Web UI**: Built with Kotlin JS Compose for a responsive and interactive interface
- **Database Integration**: MySQL database with Docker setup for easy deployment
- **RESTful API**: Spring Boot backend with comprehensive CRUD operations
- **Search & Filter**: Real-time search and filtering capabilities
- **Add New Records**: Modal form for adding new purchase records
- **Professional Design**: Clean, modern UI with Material Design principles

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Backend       │    │   Database      │
│   (Kotlin JS)   │◄──►│   (Spring Boot) │◄──►│   (MySQL)       │
│   Port: 8080    │    │   Port: 8082    │    │   Port: 3306    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 📋 Prerequisites

- **Docker Desktop**: For running MySQL database
- **Java 17+**: For Spring Boot backend
- **Kotlin 1.9.20**: For both frontend and backend
- **Gradle**: Build system

## 🛠️ Setup Instructions

### 1. Start the Database

First, start the MySQL database using Docker:

```bash
# Start MySQL and phpMyAdmin
docker-compose up -d

# Verify containers are running
docker-compose ps
```

**Database Access:**
- **MySQL**: `localhost:3306`
- **phpMyAdmin**: `http://localhost:8081`
  - Username: `automan_user`
  - Password: `automan_password`
  - Database: `automan_car_purchase`

### 2. Start the Backend API

```bash
# Navigate to backend directory
cd backend

# Build and run the Spring Boot application
./gradlew bootRun
```

The backend API will be available at: `http://localhost:8082/api`

### 3. Start the Frontend

```bash
# In a new terminal, from the root directory
./gradlew jsBrowserDevelopmentRun
```

The frontend application will be available at: `http://localhost:8080`

## 🗄️ Database Schema

The system uses a single `purchases` table with the following structure:

```sql
CREATE TABLE purchases (
    id INT AUTO_INCREMENT PRIMARY KEY,
    date VARCHAR(50) NOT NULL,
    lot_number VARCHAR(50) NOT NULL,
    car_model_number VARCHAR(100) NOT NULL,
    car_model_year VARCHAR(10) NOT NULL,
    car_name VARCHAR(100) NOT NULL,
    auction_name VARCHAR(100) NOT NULL,
    stock_location VARCHAR(100) NOT NULL,
    rixo_company VARCHAR(100) NOT NULL,
    client_name VARCHAR(100) NOT NULL,
    memo TEXT,
    price VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 🔧 API Endpoints

### Purchases
- `GET /api/purchases` - Get all purchases
- `GET /api/purchases/{id}` - Get purchase by ID
- `POST /api/purchases` - Create new purchase
- `PUT /api/purchases/{id}` - Update purchase
- `DELETE /api/purchases/{id}` - Delete purchase

### Search & Filter
- `GET /api/purchases/search?query={term}` - Search purchases
- `GET /api/purchases/filter/car-name?carName={name}` - Filter by car name
- `GET /api/purchases/filter/auction-name?auctionName={name}` - Filter by auction name
- `GET /api/purchases/filter/client-name?clientName={name}` - Filter by client name
- `GET /api/purchases/filter/date?date={date}` - Filter by date

## 🎯 Usage

### Adding a New Purchase
1. Click the **"New+"** button in the top-right corner
2. Fill in all the required fields in the modal form
3. Click **"Add Purchase"** to save to the database
4. The new record will appear in the table immediately

### Searching and Filtering
1. Use the search box to find records by any field
2. Click filter buttons to filter by specific criteria
3. Real-time results update as you type

### Viewing Data
- All purchase records are displayed in a responsive table
- Data is loaded from the MySQL database
- Loading states and error handling are implemented

## 🐛 Troubleshooting

### Database Connection Issues
- Ensure Docker Desktop is running
- Check if MySQL container is healthy: `docker-compose ps`
- Verify database credentials in `backend/src/main/resources/application.yml`

### Backend Issues
- Check if Java 17+ is installed: `java -version`
- Verify Spring Boot is running on port 8082
- Check logs for any startup errors

### Frontend Issues
- Ensure all dependencies are installed
- Check browser console for JavaScript errors
- Verify the backend API is accessible

### CORS Issues
- The backend is configured to allow CORS from multiple localhost ports
- If you're using a different port, update the CORS configuration in `PurchaseController.kt`

## 📁 Project Structure

```
automan-car-purchase/
├── backend/                          # Spring Boot API
│   ├── src/main/kotlin/
│   │   └── com/automan/backend/
│   │       ├── BackendApplication.kt
│   │       ├── controller/
│   │       │   └── PurchaseController.kt
│   │       ├── model/
│   │       │   └── Purchase.kt
│   │       ├── repository/
│   │       │   └── PurchaseRepository.kt
│   │       └── service/
│   │           └── PurchaseService.kt
│   └── src/main/resources/
│       └── application.yml
├── src/jsMain/kotlin/
│   └── com/automan/purchase/
│       ├── Main.kt
│       ├── Models.kt
│       ├── ApiService.kt
│       └── MinimalPurchaseApp.kt
├── database/
│   └── init.sql                      # Database initialization
├── docker-compose.yml                # Docker setup
├── build.gradle.kts                  # Frontend build config
└── README.md
```

## 🚀 Deployment

### Production Setup
1. **Database**: Use a production MySQL instance
2. **Backend**: Deploy Spring Boot JAR to a cloud platform
3. **Frontend**: Build and deploy the static files to a web server

### Environment Variables
Update the following in production:
- Database connection strings
- API endpoints
- CORS origins

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

---

**Happy coding! 🚗💼✨**
