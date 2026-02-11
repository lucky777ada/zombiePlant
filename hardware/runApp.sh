#!/bin/bash

# Install required system dependencies for picamera2 and adafruit-dht
echo "Updating package list and installing system dependencies..."
sudo apt-get update
sudo apt-get install -y libcap-dev libcamera-apps libgpiod-dev portaudio19-dev

# Create a virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv --system-site-packages venv
fi

# Install dependencies into the virtual environment
echo "Installing dependencies..."
venv/bin/pip install -r requirements.txt

# Run the FastAPI server using the venv's python, with sudo for hardware access
echo "Starting server on port 80..."
# Ensure PYTHONPATH includes the current directory so 'src' is found
export PYTHONPATH=$PYTHONPATH:$(pwd)
sudo -E venv/bin/uvicorn src.main:app --host 0.0.0.0 --port 80
