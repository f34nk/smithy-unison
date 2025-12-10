# AWS S3 Demo Makefile
# UCM is installed locally via compile.sh (same version as Docker)

UCM_VERSION ?= 1.0.0

.PHONY: all
all: generate

.PHONY: generate
generate: clean
	#
	# Download S3 model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/s3/service/2006-03-01/s3-2006-03-01.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/s3/service/2006-03-01/s3-2006-03-01.json
	mv s3-2006-03-01.json model/
	#
	# Generate Unison code from Smithy model
	#
	smithy build
	tree -h generated

.PHONY: clean
clean:
	rm -rf generated/*.u ./build
	rm -f compiled/main.uc
	rm -f model/*.json

.PHONY: install
install:
	#
	# Install UCM $(UCM_VERSION) from GitHub if needed
	#
	UCM_VERSION=$(UCM_VERSION) ./install.sh

.PHONY: compile
compile: generate install
	#
	# Compile Unison code locally
	#
	./compile.sh

.PHONY: test
test: generate install
	#
	# Test compilation locally (syntax check only)
	#
	./test.sh

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
