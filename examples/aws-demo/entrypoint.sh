#!/bin/bash
set -ex

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
echo "Waiting for Moto server..."
until curl --silent --fail http://moto:5050 > /dev/null 2>&1; do
    echo "Waiting for moto..."
    sleep 2
done
echo "Moto is ready!"
echo ""

echo "=== Running S3 Demo ==="
echo ""

ucm run.compiled compiled/main.uc

echo ""
echo "=== Demo Complete ==="
