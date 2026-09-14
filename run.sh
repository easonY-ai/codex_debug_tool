#!/bin/sh
set -eu
base_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
database=${TRACE_LENS_DATABASE:-"$base_dir/data/analyzer.sqlite"}
exec java -jar "$base_dir/backend/target/trace-lens-backend-0.1.0-SNAPSHOT.jar" --analyzer.database="$database" "$@"
