export HOST_PORT=4566
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)
export SERVICES=s3
export CONTAINER_NAME=$(SERVICES)-demo

.PHONY: all
all: clean generate

.PHONY: generate
generate: model/s3-2006-03-01.json
	#
	# Generate $(CONTAINER_NAME)
	#
	smithy build
	tree -h generated

.PHONY: clean
clean: docker/stop
	rm -rf generated/*.u ./build .ucm *.output.md 
	rm -f compiled/main.uc
	rm -f model/*.json
	rm -rf terraform/.terraform
	rm -f terraform/.terraform.lock.hcl
	rm -f terraform/terraform.tfstate
	rm -f terraform/terraform.tfstate.backup

# Run demo tests with UCM transcript
# Note: For reproducible tests, set AWS_SIGV4_TIMESTAMP environment variable:
#   AWS_SIGV4_TIMESTAMP=20260113T120000Z make test
.PHONY: test
test: generate docker/start terraform
	#
	# Run $(CONTAINER_NAME)
	#
	unset AWS_SESSION_TOKEN && ucm transcript demo.md

.PHONY: compile-with-lib
compile-with-lib:
	#
	# Fetch deps from Unison Share and compile $(CONTAINER_NAME) locally
	#
	ucm transcript compile-with-lib.md

.PHONY: integration-test
integration-test: compile-with-lib docker/start terraform
	#
	# Run compiled $(CONTAINER_NAME)
	#
	ucm run.compiled compiled/main.uc

# make sure model/s3-2006-03-01.json is available
model/s3-2006-03-01.json:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/s3/service/2006-03-01/s3-2006-03-01.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/s3/service/2006-03-01/s3-2006-03-01.json
	mv s3-2006-03-01.json model/

.PHONY: terraform
terraform:
	#
	# Create $(CONTAINER_NAME) infrastructure
	#
	@echo "AWS_REGION is $$AWS_REGION"
	cd terraform && terraform init && terraform apply -auto-approve

.PHONY: docker/start
docker/start: docker/stop
	#
	# Start $(CONTAINER_NAME) LocalStack
	#
	@echo "Forward port $$HOST_PORT to Localstack port 4566"
	docker run --rm -d -p $(HOST_PORT):4566 -e SERVICES=$(SERVICES) --name $(CONTAINER_NAME) localstack/localstack

.PHONY: docker/stop
docker/stop:
	docker stop $(CONTAINER_NAME) &> /dev/null || true
	docker rm $(CONTAINER_NAME) &> /dev/null || true
