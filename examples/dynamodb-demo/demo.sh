#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

UCM_INSTALL_DIR="$SCRIPT_DIR/.ucm"
UCM_BIN="$UCM_INSTALL_DIR/ucm"

export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:4566


echo "=== Starting Unison DynamoDB Demo ==="
echo ""

# Show UCM version
echo "UCM version: $($UCM_BIN version 2>&1)"
echo ""

# Verify compiled code exists
if [ ! -f "compiled/main.uc" ]; then
    echo "ERROR: compiled/main.uc not found!"
    echo "Run 'make compile' locally first."
    exit 1
fi

echo "Found compiled bytecode: compiled/main.uc"
ls -la compiled/main.uc
echo ""

# Wait for moto to be available
echo "Waiting for LocalStack ..."
until curl --silent --fail http://localhost:4566 > /dev/null 2>&1; do
    echo "Waiting for LocalStack..."
    sleep 2
done
echo "LocalStack is ready!"
echo ""

echo "=== Running DynamoDB Demo ==="
echo ""

$UCM_BIN run.compiled compiled/main.uc

echo ""
echo "=== Demo Complete ==="
