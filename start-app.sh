#!/bin/bash
cd /home/jahazielo/V.E.C.T.O.R
pkill -9 -f "java -jar" 2>/dev/null
sleep 2
nohup java -jar target/vector-1.0.0.jar > /tmp/vector.log 2>&1 &
echo "App starting... Check /tmp/vector.log for progress"
sleep 10
echo "Testing API..."
curl -s --max-time 5 http://localhost:8080/api/models || echo "App not ready yet, wait a bit longer"
