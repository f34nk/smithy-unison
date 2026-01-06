#!/bin/bash
set -e

echo "
Installs/updates UCM from GitHub releases
    https://github.com/unisonweb/unison/releases
"

UCM_VERSION="${UCM_VERSION:-1.0.2}"
UCM_INSTALL_DIR=$HOME/.ucm
UCM_BIN=$UCM_INSTALL_DIR/ucm

# =============================================================================
# Install/Update UCM
# =============================================================================

install_ucm() {
    echo "Installing UCM ${UCM_VERSION}..."
    
    # Detect architecture
    ARCH=$(uname -m)
    case "$ARCH" in
        arm64|aarch64) UCM_ARCH="arm64" ;;
        x86_64|amd64) UCM_ARCH="x64" ;;
        *) echo "ERROR: Unsupported architecture: $ARCH"; exit 1 ;;
    esac
    
    # Detect OS
    OS=$(uname -s)
    case "$OS" in
        Darwin) UCM_OS="macos" ;;
        Linux) UCM_OS="linux" ;;
        *) echo "ERROR: Unsupported OS: $OS"; exit 1 ;;
    esac
    
    # Download URL
    UCM_URL="https://github.com/unisonweb/unison/releases/download/release/${UCM_VERSION}/ucm-${UCM_OS}-${UCM_ARCH}.tar.gz"
    
    echo "Downloading from: $UCM_URL"
    
    # Create install directory
    rm -rf "$UCM_INSTALL_DIR"
    mkdir -p "$UCM_INSTALL_DIR"
    if [ ! -d "$UCM_INSTALL_DIR" ]; then
        echo "ERROR: Failed to create install directory: $UCM_INSTALL_DIR"
        exit 1
    fi
    # Download and extract
    curl -fsSL "$UCM_URL" | tar -xz -C "$UCM_INSTALL_DIR"
    
    # Make executable
    chmod +x "$UCM_BIN"
    
    echo "UCM installed to: $UCM_INSTALL_DIR"
}

# Check if UCM needs to be installed or updated
if [ ! -f "$UCM_BIN" ]; then
    echo "UCM not found"
    install_ucm
else
    CURRENT_VERSION=$("$UCM_BIN" version 2>&1 || echo "unknown")
    echo "Current UCM: $CURRENT_VERSION"
    
    # Check if version matches
    if echo "$CURRENT_VERSION" | grep -q "$UCM_VERSION"; then
        echo "UCM version OK"
    else
        echo "UCM version mismatch. Reinstalling..."
        install_ucm
    fi
fi
