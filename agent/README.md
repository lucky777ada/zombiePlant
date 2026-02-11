# neoZombiePlant - Autonomous Hydroponic Agent

**neoZombiePlant** is an intelligent agentic system designed to autonomously manage a hydroponic gardening unit. Powered by Large Language Models (LLMs), the agent ("Alvaro") monitors plant health, controls environmental factors, and manages nutrient dosing to ensure a successful harvest.

## Features

- **Autonomous Operation:** The agent monitors sensors and executes actions (pumps, lights, etc.) without human intervention in `AUTO` mode.
- **Human-in-the-Loop:** `MANUAL` mode allows the agent to propose actions and wait for user approval.
- **Lifecycle Management:** Adapts care strategies (pH targets, nutrient strength, lighting) based on the plant's growth stage (Seedling, Vegetative, Flowering).
- **Hardware Integration:**
    -   **Sensors:** pH, TDS, Water Level, Temperature, Humidity.
    -   **Actuators:** Peristaltic pumps (Nutrients/pH), Main Water Pump, Grow Lights (AC Relay).
    -   **Vision:** Camera integration for plant health monitoring.
- **Flexible "Brains":** Supports multiple LLM providers (Google Gemini, OpenAI, OpenRouter, Local LLMs via LM Studio).

## Setup & Configuration

### Prerequisites
-   Java/Kotlin environment (JDK 17+)
-   Hardware controller (ESP32/Raspberry Pi) running the compatible API at `http://192.168.1.160`.
-   API Key for the chosen LLM provider (e.g., Google Gemini).

### Configuration
1.  **Clone the repository.**
2.  **Create `local.properties`** in the project root (this file is git-ignored).
3.  **Add your configuration keys:**
    ```properties
    # LLM Keys
    gemini.api.key=YOUR_GEMINI_KEY
    # lmstudio.api.url=http://localhost:1234 (Optional, for local LLM)

    # Hardware API
    hydroponic.api.url=http://192.168.1.160

    # Database
    db.url=jdbc:postgresql://192.168.1.31:5432/zombie_plant
    db.user=zombie
    db.password=z0mbi3Lif3
    ```

## Usage

Run the `Main.kt` file to start the agent.

1.  **Select Mode:**
    -   `1. AUTO`: The agent runs continuously, making decisions based on sensor data and its implementation plan.
    -   `2. MANUAL`: The agent suggests actions but requires valid user confirmation before interacting with hardware.

2.  **Initial Checks:**
    -   The system will verify the germination date and plant status.
    -   It will then enter the main control loop.

## Project Structure

-   `src/main/kotlin/Main.kt`: Entry point and main loop.
-   `src/main/kotlin/llm/`: Logic for the AI agent, including prompts and "Brain" configuration.
-   `src/main/kotlin/tools/`: Integration with the hardware API and system tools.
-   `src/main/kotlin/planner/`: State management and biological logic.

## Disclaimer

This is a research project for autonomous agriculture. Use at your own risk. Ensure hardware safety mechanisms (overflow protection, electrical safety) are in place.
