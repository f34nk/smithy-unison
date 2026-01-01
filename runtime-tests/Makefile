# UCM is installed locally via install.sh

UCM_VERSION ?= 1.0.0

.PHONY: all
all: clean copy test

.PHONY: copy
copy:
	#
	# Copy runtime modules to src/
	#
	cp -r ../src/main/resources/runtime/*json*.u src/
	cp -r ../src/main/resources/runtime/*xml*.u src/
	cp -r ../src/test/resources/runtime/*_test.u src/

.PHONY: clean
clean:
	rm -rf src/*.u ./build *.output.md

.PHONY: test
test:
	#
	# Run runtime tests
	#
	./test.sh
