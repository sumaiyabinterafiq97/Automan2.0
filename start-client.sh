#!/bin/bash

echo "🚀 Starting Automan Client Application..."

# Initialize MySQL database if not exists
if [ ! -d "/var/lib/mysql/automan_car_purchase" ]; then
    echo "📊 Initializing MySQL database..."
    
    # Start MySQL temporarily for initialization
    mysqld_safe --user=mysql --datadir=/var/lib/mysql &
    MYSQL_PID=$!
    
    # Wait for MySQL to start
    sleep 10
    
    # Create database and user
    mysql -e "CREATE DATABASE IF NOT EXISTS automan_car_purchase;"
    mysql -e "CREATE USER IF NOT EXISTS 'automan_user'@'localhost' IDENTIFIED BY 'automan_password';"
    mysql -e "GRANT ALL PRIVILEGES ON automan_car_purchase.* TO 'automan_user'@'localhost';"
    mysql -e "FLUSH PRIVILEGES;"
    
    # Import initial data
    if [ -f "/docker-entrypoint-initdb.d/init.sql" ]; then
        echo "📥 Importing initial database schema..."
        mysql -u automan_user -pautoman_password automan_car_purchase < /docker-entrypoint-initdb.d/init.sql
    fi
    
    # Stop temporary MySQL
    kill $MYSQL_PID
    sleep 5
fi

echo "✅ Database ready!"
echo "🌐 Starting all services with supervisor..."

# Start supervisor to manage all services
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
