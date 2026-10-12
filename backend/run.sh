#!/usr/bin/env bash
# Starts the API on macOS/Linux: loads backend/.env (if present), then runs Gradle.
# Usage, from the backend folder:  ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
if [ -f .env ]; then
  # Export every KEY=value line; empty values stay unset so the API's defaults apply.
  while IFS= read -r line || [ -n "$line" ]; do
    [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=[[:space:]]*(.*)$ ]] || continue
    name="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
    value="${value%\"}"; value="${value#\"}"; value="${value%\'}"; value="${value#\'}"
    [ -n "$value" ] && export "$name=$value"
  done < .env
  echo "Loaded .env"
else
  echo "No .env found; using the API's defaults. Copy .env.example to .env to configure keys."
fi
exec ./gradlew run
