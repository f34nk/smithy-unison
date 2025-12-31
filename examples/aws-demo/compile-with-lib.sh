#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

UCM_INSTALL_DIR="$SCRIPT_DIR/.ucm"
UCM_BIN="$UCM_INSTALL_DIR/ucm"

CODEBASE=$(mktemp -d)
OUTPUT_FILE="$(pwd)/compiled/main"

cat > /tmp/compile.md << 'EOF'
```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
scratch/main> lib.install @unison/http/releases/8.0.0
scratch/main> lib.install @f34nk/aws/releases/0.1.0
scratch/main> load src/main.u
scratch/main> add
scratch/main> compile main OUTPUT_FILE
```
EOF

# Replace placeholder with actual path
sed -i '' "s|OUTPUT_FILE|$OUTPUT_FILE|" /tmp/compile.md

$UCM_BIN -C "$CODEBASE" transcript /tmp/compile.md

EXIT_CODE=$?
rm -rf "$CODEBASE"
exit $EXIT_CODE
