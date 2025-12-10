# AWS S3 Demo

End-to-end test of the generated S3 client against a mock S3 server (Moto).

## Prerequisites

- Docker (minimal memory needed - just runs pre-compiled code)
- `curl` for downloading UCM

## Quick Start

```bash
make docker/test
```

This will:
1. Generate Unison code from Smithy model
2. Install UCM locally (from GitHub releases, same version as Docker)
3. Compile all Unison code locally
4. Build Docker containers
5. Run the compiled demo against Moto mock S3

## How It Works

**Compilation happens locally** to avoid memory issues in Docker. The `compile.sh` script:
1. Downloads UCM from GitHub releases (same version Docker uses)
2. Compiles all code to `compiled/main.uc`
3. Docker copies and runs the pre-compiled bytecode

This ensures **UCM version consistency** between compile and run.

## Make Targets

| Target | Description |
|--------|-------------|
| `make generate` | Generate Unison code from Smithy model |
| `make compile` | Install UCM + compile (generates `compiled/main.uc`) |
| `make test-local` | Run local syntax check (no Docker) |
| `make docker/test` | Full end-to-end test in Docker |
| `make clean` | Remove generated and compiled files |

## UCM Version

Both `compile.sh` and Docker use the same UCM version (default: 1.0.0).
To use a different version:

```bash
UCM_VERSION=1.0.0 make docker/test
```

## Files

- `compile.sh` - Installs UCM and compiles code
- `test-local.sh` - Local syntax validation  
- `entrypoint.sh` - Docker entrypoint (runs compiled code)
- `src/main.u` - Demo application
- `generated/` - Generated Unison code
- `compiled/` - Compiled bytecode
- `.ucm/` - Local UCM installation (gitignored)
