# AWS S3 Demo Makefile
# UCM is installed locally via install.sh

UCM_VERSION ?= 1.0.0

.PHONY: all
all: clean generate

.PHONY: generate
generate: model/s3-2006-03-01.json
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
	rm -rf terraform/.terraform
	rm -f terraform/.terraform.lock.hcl
	rm -f terraform/terraform.tfstate
	rm -f terraform/terraform.tfstate.backup

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

.PHONY: compile-with-lib
compile-with-lib: install
	#
	# Fetch deps from Unison Share and compile locally
	#
	./compile-with-lib.sh

.PHONY: test
test: compiled/main.uc docker/start terraform
	#
	# Run compiled Unison code against LocalStack
	#
	./demo.sh

.PHONY: integration-test
integration-test: compile-with-lib docker/start terraform
	#
	# Run compiled code in Docker Compose
	#
	./demo.sh

# make sure model/s3-2006-03-01.json is available
model/s3-2006-03-01.json:
	#
	# Download S3 model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/s3/service/2006-03-01/s3-2006-03-01.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/s3/service/2006-03-01/s3-2006-03-01.json
	mv s3-2006-03-01.json model/

# make sure compiled/main.uc is up to date
compiled/main.uc: 
	make compile

.PHONY: terraform
terraform:
	#
	# Run Terraform to create the S3 bucket
	#
	cd terraform && terraform init && terraform apply -auto-approve

.PHONY: docker/start
docker/start: docker/stop
	#
	# Run LocalStack in Docker
	#
	docker run --rm -d -p 4566:4566 -e SERVICES=s3 --name localstack localstack/localstack

.PHONY: docker/stop
docker/stop:
	docker stop localstack &> /dev/null || true
	docker rm localstack &> /dev/null || true
