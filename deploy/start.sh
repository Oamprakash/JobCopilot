#!/bin/sh
# Starts the API, waits until it answers, then runs the web app in the foreground on $PORT.
# If either process exits, the container exits so Render restarts it.
set -e
: "${PORT:=10000}"

java $JAVA_OPTS -jar /app/api.jar &
API_PID=$!

i=0
until node -e "fetch('http://127.0.0.1:${API_PORT:-8080}/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"; do
  if ! kill -0 "$API_PID" 2>/dev/null; then echo "API exited during startup" >&2; exit 1; fi
  i=$((i + 1))
  if [ "$i" -gt 120 ]; then echo "API did not start within 120 s" >&2; exit 1; fi
  sleep 1
done
echo "API is up; starting web on port $PORT"

cd /app/web
node node_modules/next/dist/bin/next start -p "$PORT" -H 0.0.0.0 &
WEB_PID=$!

# Exit as soon as either process stops.
while kill -0 "$API_PID" 2>/dev/null && kill -0 "$WEB_PID" 2>/dev/null; do sleep 5; done
echo "A process exited; stopping" >&2
kill "$API_PID" "$WEB_PID" 2>/dev/null || true
exit 1
