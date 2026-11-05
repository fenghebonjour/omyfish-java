#!/bin/sh
# Orchestrates cache-failure.js: starts k6 in the background, stops the redis container
# partway through the run, restarts it, then waits for k6 to finish. Run from the repo root.
set -eu

cd "$(dirname "$0")/../../.."

docker run --rm --network omyfish-java_omyfish-net \
    -v "$(pwd)/services/shop-service/loadtest/scripts:/scripts:ro" \
    -e BASE_URL=http://shop-service:8080 \
    grafana/k6 run /scripts/cache-failure.js &
K6_PID=$!

sleep 30
echo ">>> stopping redis mid-run"
docker compose stop redis

sleep 30
echo ">>> restarting redis"
docker compose start redis

wait "$K6_PID"
