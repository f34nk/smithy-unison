.PHONY: all
all: clean generate

.PHONY: generate
generate:
	# 
	# Generate Unison code from Smithy model
	#
	smithy build
	tree -h generated

.PHONY: clean
clean:
	rm -rf generated/*.u ./build
