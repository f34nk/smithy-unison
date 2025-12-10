#!/bin/bash
set -e

# Run moto_server using uv (which knows about the virtual environment)
uv run moto_server -H "0.0.0.0" -p 5050
