#!/bin/bash
echo "Monitoring purchases table for new records..."
echo "Press Ctrl+C to stop"
echo "----------------------------------------"

while true; do
    echo "$(date): Current record count: $(docker exec -it automan_mysql mysql -u root -prootpassword -e "USE automan_car_purchase; SELECT COUNT(*) FROM purchases;" 2>/dev/null | tail -n 1)"
    sleep 2
done
