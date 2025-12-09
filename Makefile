X:=$(shell find examples -type d -not -name examples -maxdepth 1 -exec basename {} \;)
EXAMPLES:=$(foreach x,$(X),examples/$(x)/)

.PHONY: all
all: clean build test

.PHONY: build
build:
	#
	# Build and publish the generator
	#
	rm -rf build-errors.log
	./gradlew clean build publishToMavenLocal 2>build-errors.log
	[ -s build-errors.log ] || rm -rf build-errors.log
	tree build/libs
	tree ~/.m2/repository/io/smithy/unison/smithy-unison

.PHONY: test
test: test/java

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

.PHONY: clean
clean:
	#
	# Clear the build
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
			echo "Error building $$example" ; \
			exit 1 ; \
		fi ; \
	done

# Usage: make examples/error-types
.PHONY: $(EXAMPLES)
examples/%: $(EXAMPLES)
	#
	# Build $@
	#
	cd $@ && make

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

# .PHONY: demo
# demo:
# 	cd examples/aws-demo && \
# 	make docker/test
