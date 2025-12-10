.PHONY: all
all: 
	@echo "Usage: make build"

.PHONY: init
init: 
	#
	# Initialize AWS models from https://github.com/aws/api-models-aws
	#
	if [ ! -d api-models-aws-main ]; then \
		wget "https://github.com/aws/api-models-aws/archive/refs/heads/main.zip" -O "api-models-aws.zip"; \
		unzip -q api-models-aws.zip; \
		rm api-models-aws.zip; \
	fi

.PHONY: build
build: init
	#
	# Build AWS SDK models
	#
	./generate.py
	tree -h output/*/src
	
.PHONY: clean
clean:
	rm -rf output
