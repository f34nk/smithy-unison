# X:=$(shell find examples -type d -not -name examples -maxdepth 1 -exec basename {} \;)
# EXAMPLES:=$(foreach x,$(X),examples/$(x)/)
# TMPDIR:=build/tmp

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
# test: test/java test/resources
test: test/java

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

# .PHONY: test/resources
# test/resources:
# 	#
# 	# Run JAVA resources tests
# 	#
# 	rm -rf "$(TMPDIR)" && \
# 	mkdir -p "$(TMPDIR)/test" && \
# 	find src/*/resources -type f -name *.erl -exec cp {} "$(TMPDIR)/test/" \;
# 	echo \
# 	{erl_opts, [debug_info]}.\\n\
# 	{deps, []}.\\n\
# 	{eunit_opts, [verbose]}. >> "$(TMPDIR)/rebar.config" && \
#     tree $(TMPDIR) && \
#     cd "$(TMPDIR)" && \
#     find test/ -type f -name "*_test.erl" | \
#     xargs -I {} basename {} | \
#     sed 's/_test.erl/_test/g' | \
#     xargs -I {} echo "rebar3 eunit --module={}" | \
#     xargs -I {} sh -c {}

#     # 1. Find all test modules
#     # 2. Get the base name of the test module
#     # 3. Remove the _test.erl suffix
#     # 4. Echo the command to run the test module
#     # 5. Execute the command

.PHONY: clean
clean:
	#
	# Clear the build
	#
	rm -rf build bin test-errors.log build-errors.log
	rm -rf ~/.m2/repository/io/smithy/erlang/smithy-erlang

# # Usage: make examples
# .PHONY: examples
# examples: examples/clean
# 	#
# 	# Build $(EXAMPLES)
# 	#
# 	@for x in $(EXAMPLES); do \
# 		example=`echo $$x|sed 's/\/$$//g'` ; \
# 		make $$example ; \
# 		if [ $$? -ne 0 ]; then \
# 			echo "Error building $$example" ; \
# 			exit 1 ; \
# 		fi ; \
# 	done

# # Usage: make examples/user-service
# .PHONY: $(EXAMPLES)
# examples/%: $(EXAMPLES)
# 	#
# 	# Build $@
# 	#
# 	cd $@ && make

# # Usage: make examples/clean
# examples/clean:
# 	#
# 	# Build $(EXAMPLES)
# 	#
# 	@for x in $(EXAMPLES); do \
# 		cd $$x ; \
# 		make clean ; \
# 		cd - ; \
# 	done

# .PHONY: demo
# demo:
# 	cd examples/aws-demo && \
# 	make docker/test
