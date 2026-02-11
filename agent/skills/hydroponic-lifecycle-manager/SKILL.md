---
name: hydroponic-lifecycle-manager
description: Provides agronomic logic for managing the complete plant life cycle (Seedling to Harvest). It guides decision-making for nutrient dosing, pH balancing, and hardware troubleshooting to minimize human intervention.
---

## When to use this skill

Activate this skill when the `NeoZombiePlant` agent is:
1.  **Determining Target States:** Calculating the optimal TDS (Total Dissolved Solids) or pH setpoints based on the plant's age/growth stage rather than a static default.
2.  **Analyzing Trends:** When sensor readings deviate (e.g., pH drift), use this to decide if a reaction (Flush/Dose) is actually necessary or if it is sensor noise.
3.  **Troubleshooting:** When hardware commands (e.g., `Refill`) fail to produce expected state changes (indicating air-locks or leaks).

## How to use it

This skill provides the logic model for the `CyberGardenerActions.kt`. It divides the lifecycle into three critical stages.

### 1. Determine Growth Stage Targets
*Current logic uses static values. Update `HydroponicState` to track `days_since_germination` and apply these dynamic targets:*

| Stage | Duration (Approx) | Target TDS (EC) | Target pH | Lighting |
| :--- | :--- | :--- | :--- | :--- |
| **Seedling** | Day 0–14 | 300–500 ppm | 5.8–6.3 | 18/6 or 16/8 |
| **Vegetative** | Day 15–35 | 800–1200 ppm | 5.5–6.0 | 18/6 or 16/8 |
| **Flowering** | Day 36+ | 1000–1400 ppm | 6.0–6.5 | 12/12 (Critical) |

> **Correction for "Nutrient Efficiency" Challenge:** Do not dose to 1000+ ppm during the Seedling stage. This causes nutrient burn.

### 2. Dosing & Flushing Logic (The Anti-Oscillation Rule)
*To solve "Aggressive Flushing", implement the following decision tree:*

1.  **Read Sensor:** pH is 7.5 (High).
2.  **Verify Stability:** Take 3 readings with 10-second delays.
    * *If variance > 0.2:* Sensor is noisy. **Action:** Wait/Clean Sensor.
    * *If stable:* Proceed.
3.  **Calculate Adjustment:**
    * Is TDS also high? -> **Action:** `System Flush`.
    * Is TDS low/normal? -> **Action:** `pH Down` (Micro-dose).
4.  **Drift Tolerance:** Allow pH to drift between 5.5 and 6.5. Only correct if it hits < 5.0 or > 7.0. *Stop correcting minor deviations.*

### 3. Hardware Troubleshooting Protocols

**Problem: Pump Air-Locks (Refill Failure)**
*Detection:* `Water Level` does not increase after `Fill Tank` action executes for 30 seconds.
*Resolution Algorithm:*
1.  Stop `Fill Tank`.
2.  Wait 5 seconds.
3.  Pulse `Fill Tank` (On for 2s, Off for 2s) x 5 times.
4.  Re-check water level.
5.  If fail, alert Human (Manual Mode).

**Problem: Algae Growth**
*prevention:* While this is physical, the agent can monitor `TDS` drift.
*Detection:* TDS rises unexpectedly while Water Level is constant (suggests organic breakdown/activity unrelated to plant uptake).
*Action:* Schedule `System Flush` and set `Light Leak Check` flag.

## Integration Notes (Kotlin)

- **State Object:** Extend `HydroponicState` to include `currentStage` (Enum: SEEDLING, VEG, FLOWER).
- **GOAP Cost:** Increase the cost of `System Flush` significantly (e.g., cost: 100) compared to `Micro Dose` (cost: 10) to force the planner to prefer correction over resetting.
