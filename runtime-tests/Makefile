# UCM is installed locally via install.sh

UCM_VERSION ?= 1.0.0

.PHONY: all
all: clean copy install

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
	rm -rf src/*.u ./build .ucm *.output.md

.PHONY: install
install:
	#
	# Install UCM $(UCM_VERSION) from GitHub if needed
	#
	UCM_VERSION=$(UCM_VERSION) ./install.sh

.PHONY: test
test: copy
	#
	# Run runtime tests
	#
	./test.sh
