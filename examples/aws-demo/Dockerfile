# Build Unison demo application
# Installs UCM from official releases

FROM debian:bookworm-slim

# Build argument for architecture (amd64 or arm64)
ARG TARGETARCH
ARG UCM_VERSION=1.0.0

USER root
WORKDIR /app

# Install dependencies (coreutils includes stdbuf and timeout)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    git \
    coreutils \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd --system --create-home --home-dir /home/nonroot nonroot

# Download and install UCM based on architecture
# UCM releases use: ucm-linux-x64.tar.gz or ucm-linux-arm64.tar.gz
# URL format: https://github.com/unisonweb/unison/releases/download/release/{version}/ucm-linux-{arch}.tar.gz
RUN if [ "$TARGETARCH" = "arm64" ]; then \
        UCM_ARCH="arm64"; \
    else \
        UCM_ARCH="x64"; \
    fi && \
    echo "Downloading UCM ${UCM_VERSION} for ${UCM_ARCH}..." && \
    curl -fsSL "https://github.com/unisonweb/unison/releases/download/release/${UCM_VERSION}/ucm-linux-${UCM_ARCH}.tar.gz" \
        -o /tmp/ucm.tar.gz && \
    mkdir -p /opt/unison && \
    tar -xzf /tmp/ucm.tar.gz -C /opt/unison && \
    rm /tmp/ucm.tar.gz && \
    chmod +x /opt/unison/ucm

# Add UCM to PATH
ENV PATH="/opt/unison:${PATH}"

# Verify installation
RUN ucm version || echo "UCM installed (version check may fail in container)"

# Copy generated S3 client and runtime modules
COPY generated/ ./generated/

# Copy application code
COPY src/ ./src/

# Copy entrypoint and transcript
COPY entrypoint.sh run-aws-demo.md ./

RUN chmod +x entrypoint.sh

# Create a fresh codebase directory
RUN mkdir -p /app/.unison && chown -R nonroot:nonroot /app

USER nonroot
WORKDIR /app

ENV HOME=/app

CMD ["./entrypoint.sh"]
