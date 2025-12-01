#!/bin/bash
cd /Users/sumaiyabinterafiq/Development/Automan2.0
./gradlew jsBrowserDevelopmentWebpack --continuous &
WEBPACK_PID=$!
echo "Webpack dev server starting... PID: $WEBPACK_PID"
echo "Frontend will be available at http://localhost:8081"
echo "To stop: kill $WEBPACK_PID"
wait $WEBPACK_PID
