X:=$(shell find examples -type d -not -name examples -maxdepth 1 -exec basename {} \;)
EXAMPLES:=$(foreach x,$(X),examples/$(x)/)

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
	rm -rf build bin test-errors.log build-errors.log
	rm -rf ~/.m2/repository/io/smithy/unison/smithy-unison

# Usage: make examples
.PHONY: examples
examples: examples/clean
	#
	# Build $(EXAMPLES)
	#
	@for x in $(EXAMPLES); do \
		example=`echo $$x|sed 's/\/$$//g'` ; \
		make $$example ; \
		if [ $$? -ne 0 ]; then \
			echo "The example $$example failed" ; \
			return 1 ; \
		fi ; \
	done

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

# Usage: make examples/clean
examples/clean:
	#
	# Build $(EXAMPLES)
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
