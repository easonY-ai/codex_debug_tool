#!/bin/sh
set -eu
cd "$(dirname "$0")/frontend"
npm ci
npm test
npm run build
cd ../backend
./mvnw -B -ntp verify
printf '%s\n' "Package: backend/target/trace-lens-backend-0.1.0-SNAPSHOT.jar"
