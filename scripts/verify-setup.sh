#!/bin/bash
# verify-setup.sh - Verification script for Automan setup
# This script checks that all database migrations ran correctly

echo "🔍 Verifying Automan Setup..."
echo "=============================="
echo ""

# Check Docker is running
if ! docker ps >/dev/null 2>&1; then
    echo "❌ Docker is not running"
    echo "   Please start Docker Desktop first"
    exit 1
fi
echo "✅ Docker is running"

# Check containers are up
echo ""
echo "🐳 Checking containers..."
containers=("automan_mysql_multiplatform" "automan_backend_multiplatform" "automan_frontend_multiplatform")
all_running=true
for container in "${containers[@]}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        echo "✅ Container $container is running"
    else
        echo "❌ Container $container is NOT running"
        all_running=false
    fi
done

if [ "$all_running" = false ]; then
    echo ""
    echo "⚠️  Some containers are not running. Please start them first:"
    echo "   docker-compose -f docker/docker-compose.multiplatform.yml up -d"
    exit 1
fi

# Wait a bit for MySQL to be ready
echo ""
echo "⏳ Waiting for MySQL to be ready..."
sleep 3

# Check database connection
echo ""
echo "📊 Checking database connection..."
if docker exec automan_mysql_multiplatform mysqladmin ping -h localhost --silent 2>/dev/null; then
    echo "✅ MySQL is ready"
else
    echo "❌ MySQL is not responding"
    exit 1
fi

# Check database tables
echo ""
echo "📋 Checking database tables..."
tables=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; SHOW TABLES;" 2>/dev/null | grep -v "Tables_in" | tr -d '\r')

required_tables=("purchases" "clients" "users" "bookings" "car_brand_mapping" "booking_mappings" "rixo_prices" "vessels" "booking_calculations" "events")
missing_tables=()

for table in "${required_tables[@]}"; do
    if echo "$tables" | grep -qi "$table"; then
        echo "✅ Table $table exists"
    else
        echo "❌ Table $table is MISSING"
        missing_tables+=("$table")
    fi
done

if [ ${#missing_tables[@]} -gt 0 ]; then
    echo ""
    echo "⚠️  Missing tables: ${missing_tables[*]}"
    echo "   Database migrations may not have run correctly"
    exit 1
fi

# Check critical columns in purchases table
echo ""
echo "📋 Checking purchases table columns..."
columns=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; DESCRIBE purchases;" 2>/dev/null)

critical_columns=("total_cnf_price" "shipped" "consignee" "vessel" "shipment_size" "CC" "shift" "steering_wheel" "WD" "auction_house" "tax_total" "venue_id" "package_price" "is_package_mode" "car_pictures")
missing_columns=()

for col in "${critical_columns[@]}"; do
    if echo "$columns" | grep -qi "$col"; then
        echo "✅ Column $col exists"
    else
        echo "❌ Column $col is MISSING"
        missing_columns+=("$col")
    fi
done

if [ ${#missing_columns[@]} -gt 0 ]; then
    echo ""
    echo "⚠️  Missing columns: ${missing_columns[*]}"
    echo "   Database migrations may not have run correctly"
    exit 1
fi

# Check sample data
echo ""
echo "📦 Checking sample data..."
user_count=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; SELECT COUNT(*) FROM users;" 2>/dev/null | tail -1 | tr -d ' ')

if [ -n "$user_count" ] && [ "$user_count" -gt 0 ]; then
    echo "✅ Users exist: $user_count"
else
    echo "⚠️  No users found (this may be okay if migrations haven't run yet)"
fi

client_count=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; SELECT COUNT(*) FROM clients;" 2>/dev/null | tail -1 | tr -d ' ')

if [ -n "$client_count" ] && [ "$client_count" -gt 0 ]; then
    echo "✅ Clients exist: $client_count"
fi

purchase_count=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; SELECT COUNT(*) FROM purchases;" 2>/dev/null | tail -1 | tr -d ' ')

if [ -n "$purchase_count" ]; then
    echo "✅ Purchases exist: $purchase_count"
fi

# Check car brand mappings
mapping_count=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; SELECT COUNT(*) FROM car_brand_mapping;" 2>/dev/null | tail -1 | tr -d ' ')

if [ -n "$mapping_count" ] && [ "$mapping_count" -gt 0 ]; then
    echo "✅ Car brand mappings exist: $mapping_count"
else
    echo "⚠️  No car brand mappings found"
fi

# Check rixo prices
rixo_count=$(docker exec automan_mysql_multiplatform mysql -u automan_user -pautoman_password \
  -e "USE automan_car_purchase; SELECT COUNT(*) FROM rixo_prices;" 2>/dev/null | tail -1 | tr -d ' ')

if [ -n "$rixo_count" ] && [ "$rixo_count" -gt 0 ]; then
    echo "✅ Rixo prices exist: $rixo_count"
else
    echo "⚠️  No Rixo prices found"
fi

# Check API is responding
echo ""
echo "🌐 Checking API endpoints..."
if curl -s -f http://localhost:8083/api/auth/users/count >/dev/null 2>&1; then
    echo "✅ Backend API is responding"
else
    echo "⚠️  Backend API is not responding (may still be starting)"
    echo "   Wait a bit longer and check: curl http://localhost:8083/api/auth/users/count"
fi

# Check frontend is responding
if curl -s -f http://localhost:8080 >/dev/null 2>&1; then
    echo "✅ Frontend is responding"
else
    echo "⚠️  Frontend is not responding (may still be starting)"
    echo "   Wait a bit longer and check: curl http://localhost:8080"
fi

echo ""
echo "=============================="
if [ ${#missing_tables[@]} -eq 0 ] && [ ${#missing_columns[@]} -eq 0 ]; then
    echo "✅ All critical checks passed! System is ready."
    echo ""
    echo "🌐 Access Points:"
    echo "   • Frontend: http://localhost:8080"
    echo "   • Backend API: http://localhost:8083/api"
    echo ""
    echo "🔑 Login Credentials:"
    echo "   • Email: admin@automan.com"
    echo "   • Password: admin123"
    exit 0
else
    echo "⚠️  Some checks failed. Please review the output above."
    exit 1
fi

