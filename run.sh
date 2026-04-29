#!/bin/bash

# Start V.E.C.T.O.R and open browser automatically

cd "$(dirname "$0")"

echo "Building V.E.C.T.O.R..."
mvn clean package -DskipTests -q

echo "Starting V.E.C.T.O.R..."
java -jar target/vector-1.0.0.jar &
PID=$!

echo "Waiting for server..."
sleep 5

echo "Opening browser..."
xdg-open http://localhost:8080 2>/dev/null || \
open http://localhost:8080 2>/dev/null || \
start http://localhost:8080 2>/dev/null || \
echo "Open http://localhost:8080 manually"

echo ""
echo "V.E.C.T.O.R is running at http://localhost:8080"
echo "Press Ctrl+C to stop"

wait $PID