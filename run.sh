#!/usr/bin/env bash
# Builds the React app, starts the EcoAI Python service (if python3 + pip work) and starts Spring Boot.
set -e
cd "$(dirname "$0")"
(cd frontend && npm install --no-audit --no-fund && npm run build)
if command -v python3 >/dev/null 2>&1; then
  python3 -m pip install -q -r ecoai/requirements.txt && (cd ecoai && python3 service.py &) && echo "EcoAI service started on :5001"
else
  echo "python3 not found: the EcoAI forecast page will show 'service not reachable'."
fi
trap 'pkill -f "service.py" 2>/dev/null || true' EXIT
mvn spring-boot:run
