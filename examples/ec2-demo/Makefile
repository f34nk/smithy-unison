export HOST_PORT=4572
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)
export SERVICES=ec2
export CONTAINER_NAME=$(SERVICES)-demo

.PHONY: all
all: clean generate

.PHONY: generate
generate: model/ec2-2016-11-15.json
	#
	# Generate Unison code from Smithy model
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

.PHONY: test
test: generate docker/start terraform
	#
	# Run Unison demo against LocalStack
	#
	ucm transcript demo.md

.PHONY: compile-with-lib
compile-with-lib:
	#
	# Fetch deps from Unison Share and compile locally
	#
	ucm transcript compile-with-lib.md
	
.PHONY: integration-test
integration-test: compile-with-lib docker/start terraform
	#
	# Run compiled code
	#
	ucm run.compiled compiled/main.uc

# make sure model/dynamodb-2012-08-10.json is available
model/ec2-2016-11-15.json:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/ec2/service/2016-11-15/ec2-2016-11-15.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/ec2/service/2016-11-15/ec2-2016-11-15.json
	mv ec2-2016-11-15.json model/

.PHONY: terraform
terraform:
	#
	# Run Terraform to create infrastructure
	#
	@echo "AWS_DEFAULT_REGION is $$AWS_DEFAULT_REGION"
	cd terraform && terraform init && terraform apply -auto-approve

.PHONY: docker/start
docker/start: docker/stop
	#
	# Run LocalStack in Docker
	#
	@echo "Forward port $$HOST_PORT to Localstack port 4566"
	docker run --rm -d -p $(HOST_PORT):4566 -e SERVICES=$(SERVICES) --name $(CONTAINER_NAME) localstack/localstack

.PHONY: docker/stop
docker/stop:
	docker stop $(CONTAINER_NAME) &> /dev/null || true
	docker rm $(CONTAINER_NAME) &> /dev/null || true
