# ZombiePlant Master Plan: The Gardener's Toolkit

This document outlines the evolution of the ZombiePlant project from a collection of raw hardware endpoints into a cohesive, intelligent gardening system. The goal is to empower an external Agent to manage the plant's full lifecycle with higher-level "tools" rather than individual hardware toggles.

## 1. Current State Assessment
*   **Hardware:**
    *   **Pumps:** 2x Main (Fill/Drain), 3x Dosing (Micro, Gro, Bloom).
    *   **Sensors:** pH, TDS (EC), Float Switches (Top/Bottom), Temp/Humidity (DHT), Camera, Microphone.
    *   **Control:** AC Relay (Controls Main Grow Light + **Air Pump with 2 Air Stones**).
*   **Capabilities:**
    *   The air stones provide oxygenation and effectively mix nutrients within ~3 minutes when the AC Relay is active.
*   **Software (API):**
    *   Basic control (`/control/pump`, `/control/ac_relay`).
    *   Logic blocks (`fill_to_max`, `empty_tank`).
    *   **Gap:** High-level actions like "Feed" or "Clean" are either missing or inefficiently implemented.

## 1.5. Phase 1.5: Asynchronous Control
*Target: `/jobs/` API Router*

To prevent blocking requests during long-running operations (filling, feeding), we introduce a job-based architecture.

### **1. Submit Job (`POST /jobs`)**
*   **Concept:** Queue a background task and return immediately.
*   **Input:** `JobRequest` (type: Enum, params: dict).
*   **Output:** `job_id`.
*   **Supported Job Types:**
    *   `FILL_TO_MAX`
    *   `EMPTY_TANK`
    *   `SYSTEM_FLUSH`
    *   `FEED`
    *   `DIAGNOSE`

### **2. Check Status (`GET /jobs/{job_id}`)**
*   **Output:** `JobStatus` (status: `queued`|`running`|`completed`|`failed`, result: any, error: str).

### **3. Implementation Details**
*   **JobManager:** In-memory manager to track `asyncio.Task` objects.
*   **State:** Jobs are transient (lost on restart), which is acceptable for this hardware prototype.

---

## 2. Phase 1: Core Lifecycle Tools
*Target: `/tools/` API Router*

These tools allow the agent to perform daily/weekly maintenance tasks autonomously.

### **1. Smart Feed Cycle (`POST /tools/feed`)** ✅

*   **Concept:** Replaces manual dosing logic with a complete drain-refill-mix cycle.
*   **Logic:**
    1.  **Empty Tank:** Drain dirty water completely.
    2.  **Pre-Dose:** Dispense specified nutrient recipe (Micro/Gro/Bloom) into the empty tank.
    3.  **Fill to Max:** Add fresh water. Turbulence from filling aids initial mixing.
    4.  **Mix:** Ensure AC Relay (Air Stones) is ON for at least 3 minutes to fully mix the solution.
    5.  **Verify:** Check TDS sensor to confirm nutrient presence.
*   **Parameters:** `recipe` (Enum: `vegetative`, `flowering`, `custom`), `amounts_ml` (if custom).

### **2. True System Flush (`POST /tools/flush`)** ✅
*   **Concept:** Rinses the roots and tank with fresh water to remove salt buildup, without wasting nutrients.
*   **Logic:**
    1.  **Empty Tank.**
    2.  **Fill to Max** (Fresh water only).
    3.  **Soak/Mix:** Turn on AC Relay (Air Stones) for X minutes to circulate fresh water through roots.
    4.  **Empty Tank** (Drain the rinse water).
    5.  **Fill to Max** (Leave with fresh water for a "water only" day, or ready for next feed).

### **3. Diagnostic Self-Check (`POST /tools/diagnose`)** ✅
*   **Concept:** Daily health check for the hardware.
*   **Logic:**
    *   **Sensor Check:** Are pH/TDS/Temp values within physical bounds? (e.g., pH -1 or 15 is a sensor error).
    *   **Pump Check (Advanced):** Run a pump for 1s and listen with the **Microphone** to verify motor noise (confirming operation).
    *   **Report:** Return a structured health status JSON.

---

## 3. Phase 2: Calibration & Precision
Enable the agent to maintain accuracy over time without code changes.

### **1. Sensor Calibration (`POST /tools/calibrate/{sensor_type}`)**
*   **Concept:** Update sensor offsets dynamically using known buffer solutions.
*   **Logic:** The user places the probe in a buffer (e.g., pH 7.0), triggers this endpoint, and the system saves the new voltage offset to a config file/DB.
*   **Sensors:** pH, TDS.

### **2. Visual Growth Tracker (`POST /tools/visual_check`)**
*   **Concept:** Standardized photo logging.
*   **Logic:**
    1.  Turn on AC Relay (Light) if off.
    2.  Capture image with "Plant Mode" settings (optimized EV/Saturation).
    3.  Return image + estimated "green pixel count" (basic growth metric).
    4.  Restore previous Light state.

---

## 4. Future Improvements (Hardware Roadmap)
Additional hardware to achieve full autonomy (weeks/months without human intervention).

### **1. Peristaltic Pumps (x2) - pH Control**
*   **Why:** Nutrients drift pH over time. Currently, we can read pH but not fix it.
*   **Add:** `pH Up` and `pH Down` pumps.
*   **New Tool:** `auto_balance_ph(target=6.0)` loop.

### **2. Capacitive Liquid Level Sensor**
*   **Why:** Float switches only tell us "Full" or "Empty."
*   **Add:** Analog sensor to read "50% full."
*   **Benefit:** Enables partial fills and precise nutrient concentration calculations (TDS math relies on known water volume).
