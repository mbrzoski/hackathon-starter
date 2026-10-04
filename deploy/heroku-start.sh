#!/usr/bin/env bash
# Heroku web dyno: the backend on :8080 (internal) and Caddy on $PORT (public, plain HTTP behind Heroku's TLS router).
# If either process exits, the dyno exits, so Heroku restarts it instead of serving a half-working app (rule 7).
set -u

java -XX:MaxRAMPercentage=60 -Dserver.port=8080 -jar /app/app.jar &
JAVA_PID=$!

HTTP_PORT="${PORT:-8000}" caddy run --config /etc/caddy/Caddyfile.tunnel --adapter caddyfile &
CADDY_PID=$!

trap 'kill "$JAVA_PID" "$CADDY_PID" 2>/dev/null' TERM INT
wait -n "$JAVA_PID" "$CADDY_PID"
EXIT=$?
kill "$JAVA_PID" "$CADDY_PID" 2>/dev/null
exit "$EXIT"
