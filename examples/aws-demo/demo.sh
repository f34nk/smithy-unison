#!/bin/bash
set -e

export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:4566

# Docker entrypoint script
# Runs pre-compiled Unison code against Moto mock S3

echo "=== Starting Unison S3 Demo ==="
echo ""

# Show UCM version
echo "UCM version: $(ucm version 2>&1)"
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

echo "=== Running S3 Demo ==="
echo ""

ucm run.compiled compiled/main.uc

echo ""
echo "=== Demo Complete ==="
