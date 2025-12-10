lock:
	uv pip install -r pyproject.toml
	rm -f uv.lock
	uv lock

install: uv.lock
	uv sync --dev
