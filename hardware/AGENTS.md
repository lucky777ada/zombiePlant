# ZombiePlant Hardware Agent Guidelines

This document provides essential instructions for autonomous agents working on the ZombiePlant Hardware codebase ("The Body").

## 1. Project Context

- **Role**: This repository is "The Body" (Raspberry Pi 5) of the autonomous system. It exposes a FastAPI interface to control hardware. "The Brain" (Android/Gemini) lives elsewhere.
- **Goal**: Reliable, safe execution of hardware commands (pumps, lights, sensors) and providing status data.
- **Hardware**: Raspberry Pi 5, 8-Channel Relay, Peristaltic Pumps, Float Switches, Analog Sensors (pH/TDS via MCP3008), Camera, Microphone.

## 2. Environment & Build

- **OS**: Raspberry Pi OS (Debian Bookworm based).
- **Python**: 3.x in a Virtual Environment (`venv`).
- **Dependency Management**: `requirements.txt`.
- **Key System Dependencies**: `ffmpeg` (for timelapse), `libcamera-apps`.

### Startup Commands

- **Full Startup** (Setup + Run):
  ```bash
  ./runApp.sh
  ```
  *Note: Uses `sudo` internally for GPIO access.*

- **Manual Server Run**:
  ```bash
  # Must run as root for GPIO access
  sudo venv/bin/uvicorn src.main:app --host 0.0.0.0 --port 80
  ```

## 3. Testing Protocols

All changes MUST be verified with tests. The project uses `pytest` with extensive hardware mocking.

### Test Commands

- **Run All Tests**:
  ```bash
  ./runTests.sh
  ```
  *Or directly: `venv/bin/pytest -v`*

- **Run a Single Test File**:
  ```bash
  ./runTests.sh tests/test_main.py
  ```

- **Run Specific Test Case**:
  ```bash
  ./runTests.sh tests/test_main.py::test_read_root
  ```

- **Manual Hardware Tests**:
  Scripts in the root directory (e.g., `test_pump1.py`) are for manual verification on physical hardware.
  Usage: `sudo venv/bin/python test_pump1.py`

## 4. Codebase Structure

- **`src/`**: Main application code.
  - **`main.py`**: FastAPI entry point and wiring.
  - **`models.py`**: Pydantic models and Enums.
  - **`state.py`**: Global state (locks).
  - **`actuators/`**: Hardware control (pumps, relays).
  - **`sensors/`**: Sensor drivers (pH, TDS, Camera, DHT).
  - **`logic/`**: Complex business logic (feed cycles, flushes, timelapse).
  - **`routers/`**: API route definitions.
- **`tests/`**: Pytest suite (mocks hardware).

## 5. Coding Standards

### Style & Formatting
- **PEP 8**: Adhere strictly.
- **Indentation**: 4 spaces.
- **Line Length**: Soft limit 100 characters.
- **Imports**:
  1. Standard Lib (`os`, `asyncio`, `typing`)
  2. Third-Party (`fastapi`, `pydantic`)
  3. Local (`src.x.y`) - **Always use absolute imports from `src`**.

### Typing & Models
- **Strict Typing**: Use type hints for all function arguments and return values.
- **Pydantic**: Use `BaseModel` for all API request/response bodies.
- **Enums**: Use `enum.Enum` for fixed hardware IDs (e.g., `PumpID`, `RelayState`).

### Naming Conventions
- **Variables/Functions**: `snake_case` (e.g., `execute_feed_cycle`).
- **Classes**: `PascalCase` (e.g., `CameraManager`).
- **Constants**: `UPPER_CASE` (e.g., `MAX_FILL_SECONDS`).
- **Private**: Prefix with `_` (e.g., `_read_voltage`).

### Error Handling
- **API**: Raise `HTTPException` (400 for input errors, 500 for hardware/system errors).
- **Hardware Safety**:
  - Always use `try...finally` blocks when toggling hardware to ensure it turns off even if code crashes.
  - **Example**:
    ```python
    try:
        pump.on()
        await asyncio.sleep(duration)
    finally:
        pump.off()
    ```

### Async/Await
- All Hardware I/O and API endpoints must be `async`.
- Use `asyncio.sleep()` instead of `time.sleep()`.
- Use `asyncio.to_thread()` for blocking CPU/OS operations (like `ffmpeg` or `shutil`).

## 6. Hardware Interaction Rules

1.  **Global Lock**: Use `src.state.system_lock` for any operation that manipulates water levels or pumps to prevent conflicting jobs.
    ```python
    async with system_lock:
        await perform_critical_task()
    ```
2.  **Mocking**: Never import `RPi.GPIO` or `spidev` at the top level without a `try/except` or conditional check if running on non-Pi hardware (tests mock this, but be careful).
3.  **Timeouts**: All pump operations MUST have a safety timeout.

## 7. Timelapse & Media

- **Images**: Stored in `timeLapse/images/`.
- **Video**: Stored in `timeLapse/video/`.
- **Timestamps**: Overlay date/time on images using `ffmpeg`.
- **Process**: Captures should be non-blocking (run in executor/thread).

## 8. Agent Behavior

- **Safety First**: Do not modify hardware safety limits (max durations) without explicit instruction.
- **Persistence**: Do not delete `requirements.txt` or start scripts.
- **Validation**: After writing code, ALWAYS run `./runTests.sh` to verify syntax and logic.
- **Planning**: Before complex refactors, check `MASTER_PLAN.md` and `projectSummary.md` for context.
