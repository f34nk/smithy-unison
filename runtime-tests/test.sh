#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

UCM_INSTALL_DIR="$SCRIPT_DIR/.ucm"
UCM_BIN="$UCM_INSTALL_DIR/ucm"

# Create temporary codebase
CODEBASE=$(mktemp -d)
echo "Using temporary codebase: $CODEBASE"

cleanup() {
    rm -rf "$CODEBASE"
}
trap cleanup EXIT

# Disable pager
export PAGER=cat
export TERM=dumb
export NO_COLOR=1
export LESS="-F -X"

# Run UCM transcript
yes "" 2>/dev/null | "$UCM_BIN" -C "$CODEBASE" transcript test.md 2>&1 | cat
