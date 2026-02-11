#!/bin/bash

# Check if venv exists
if [ ! -d "venv" ]; then
    echo "Virtual environment not found at ./venv"
    echo "Please run ./runApp.sh first to set up the environment, or create it manually."
    exit 1
fi

# Run pytest using the venv python
# "$@" allows passing arguments to the script (e.g. ./runTests.sh tests/test_main.py)
echo "Running tests..."
venv/bin/python -m pytest -v "$@"
