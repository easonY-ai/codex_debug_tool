#!/bin/sh
set -eu
base_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
exec java -jar "$base_dir/backend/target/trace-lens-backend-0.1.0-SNAPSHOT.jar" "$@"
