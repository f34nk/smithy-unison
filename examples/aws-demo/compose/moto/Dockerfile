# Debian-based
FROM python:3.12

USER root
WORKDIR /app

# Create non-root user with home directory
RUN adduser --system --home /home/nonroot nonroot

RUN apt-get update && apt-get install -y --no-install-recommends procps \
    && rm -rf /var/lib/apt/lists/*

# Install uv package manager
RUN pip install uv

COPY --chmod=755 pyproject.toml uv.lock entrypoint.sh /app/

RUN uv sync

# Ensure nonroot user can access app and has writable directories
RUN chown -R nonroot:nogroup /app

USER nonroot
WORKDIR /app

# Add virtual environment to PATH
ENV PATH="/app/.venv/bin:${PATH}"

CMD ["./entrypoint.sh"]
