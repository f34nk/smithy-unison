# AWS S3 Demo Makefile
# UCM is installed locally via compile.sh (same version as Docker)

UCM_VERSION ?= 1.0.0

.PHONY: all
all: generate

.PHONY: generate
generate: clean
	#
	# Generate Unison code from Smithy model
	#
	smithy build
	tree generated

.PHONY: clean
clean:
	rm -rf generated/*.u ./build
	rm -f compiled/main.uc

.PHONY: compile
compile: generate
	#
	# Compile Unison code locally
	# Installs UCM $(UCM_VERSION) from GitHub if needed
	#
	UCM_VERSION=$(UCM_VERSION) ./compile.sh

.PHONY: test-local
test-local: generate
	#
	# Test compilation locally (syntax check only)
	#
	./test-local.sh

.PHONY: docker/test
docker/test: compile docker/clean
	#
	# Run compiled code in Docker against Moto
	#
	UCM_VERSION=$(UCM_VERSION) docker compose build
	docker compose run --rm test

.PHONY: docker/clean
docker/clean:
	docker compose down --volumes --remove-orphans
	docker compose rm --stop --force 2>/dev/null || true
