
.PHONY: all
all: clean generate

.PHONY: generate
generate: model/dynamodb-2012-08-10.json
	#
	# Generate Unison code from Smithy model
	#
	smithy build
	tree -h generated

.PHONY: clean
clean:
	rm -rf generated/*.u ./build .ucm *.output.md
	rm -f compiled/main.uc
	rm -f model/*.json
	rm -rf terraform/.terraform
	rm -f terraform/.terraform.lock.hcl
	rm -f terraform/terraform.tfstate
	rm -f terraform/terraform.tfstate.backup

.PHONY: compile
compile: generate
	#
	# Compile Unison code locally
	#
	./run-transcript.sh compile.md

.PHONY: compile-with-lib
compile-with-lib:
	#
	# Fetch deps from Unison Share and compile locally
	#
	./run-transcript.sh compile-with-lib.md

.PHONY: test
test: compiled/main.uc docker/start terraform
	#
	# Run compiled Unison code against LocalStack
	#
	./run-compiled.sh compiled/main.uc

.PHONY: integration-test
integration-test: compile-with-lib docker/start terraform
	#
	# Run compiled code
	#
	./run-compiled.sh compiled/main.uc

# make sure model/dynamodb-2012-08-10.json is available
model/dynamodb-2012-08-10.json:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/dynamodb/service/2012-08-10/dynamodb-2012-08-10.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/dynamodb/service/2012-08-10/dynamodb-2012-08-10.json
	mv dynamodb-2012-08-10.json model/

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
	docker run --rm -d -p 4566:4566 -e SERVICES=dynamodb --name localstack localstack/localstack

.PHONY: docker/stop
docker/stop:
	docker stop localstack &> /dev/null || true
	docker rm localstack &> /dev/null || true
