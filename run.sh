#!/usr/bin/env bash
# Run the Juniper desktop app from the terminal.
# Usage: ./run.sh

set -euo pipefail

cd "$(dirname "$0")"
exec ./kotlin run -m desktopApp --no-compose-hot-reload
