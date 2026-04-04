X:=$(shell find examples -type d -not -name examples -maxdepth 1 -exec basename {} \;)
EXAMPLES:=$(foreach x,$(X),examples/$(x)/)
EXAMPLES_COUNT:=$(words $(EXAMPLES))

.PHONY: all
all: clean build

.PHONY: build
build:
	#
	# Build and publish the generator
	#
	rm -rf build-errors.log
	./gradlew clean build publishToMavenLocal 2>build-errors.log
	[ -s build-errors.log ] || rm -rf build-errors.log
	tree -h build/libs
	tree -h ~/.m2/repository/io/smithy/unison/smithy-unison

.PHONY: test
test: test/java test/runtime

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

.PHONY: test/runtime
test/runtime:
	#
	# Run runtime tests
	#
	cd src && \
	time ucm transcript test.md

.PHONY: clean
clean:
	#
	# Clean the build
	#
	rm -rf build bin test-errors.log build-errors.log example-*.log
	rm -rf ~/.m2/repository/io/smithy/unison/smithy-unison

# Usage: make examples
.PHONY: examples
examples:
	rm -rf example-*.log
	#
	# Run $(EXAMPLES_COUNT) examples in parallel
	#
	find examples -type d -not -name examples -maxdepth 1 -exec basename {} \; | xargs -P $(EXAMPLES_COUNT) -I {} sh -c ' \
		example="{}"; \
		logfile="example-$$example.log"; \
		sleep 1; \
		echo "Running: $$example" ; \
		make examples/$$example > $$logfile 2>&1; \
		if [ $$? -ne 0 ]; then \
			echo "$$example failed" ; \
			break ; \
		fi ; \
		echo "$$example passed" ; \
	'

# Usage: make examples/simple-service
.PHONY: $(EXAMPLES)
examples/%: $(EXAMPLES)
	#
	# Build $@
	#
	cd $@ && make clean && time make test; \
	make docker/stop ; \
	if grep -q "The transcript failed" demo.output.md; then \
		echo "The transcript failed: $@" ; \
		exit 1 ; \
	fi; \

# Usage: make examples/generate
examples/generate:
	#
	# Generate $(EXAMPLES)
	#
	@for x in $(EXAMPLES); do \
		cd $$x ; \
		make ; \
		cd - ; \
	done

# Usage: make examples/clean
examples/clean:
	#
	# Clean $(EXAMPLES)
	#
	@for x in $(EXAMPLES); do \
		cd $$x ; \
		make clean ; \
		cd - ; \
	done

.PHONY: integration-test
integration-test/%:
	#
	# Install the AWS SDK from Unison Share and 
	# run the demo against mocked infrastructure
	#
	name=$(shell echo $@|sed 's/integration-test\///g') && \
	cd examples/$$name-demo && \
	make clean && \
	make integration-test
