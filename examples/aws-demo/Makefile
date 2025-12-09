.PHONY: all
all: clean generate

.PHONY: generate
generate:
	# 
	# Generate Unison code from Smithy model
	#
	smithy build
	tree generated

.PHONY: clean
clean:
	rm -rf generated/*.u ./build

.PHONY: docker/test
docker/test: clean generate docker/clean
	docker compose build && docker compose run --rm test

.PHONY: docker/clean
docker/clean:
	docker compose down --volumes --remove-orphans && docker compose rm --stop --force
