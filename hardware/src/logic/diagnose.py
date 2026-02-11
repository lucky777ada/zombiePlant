import asyncio
import numpy as np
import wave
import os
from typing import Dict, Optional
from src.models import (
    DiagnosticResponse, SensorCheckResult, PumpCheckResult, 
    PumpID, StatusResponse
)
from src.sensors.ph import ph_sensor
from src.sensors.tds import tds_sensor
from src.sensors.dht import dht_sensor
from src.sensors.float_switches import water_level
from src.sensors.microphone import microphone
from src.actuators.pumps import pump_controller

# Thresholds for valid sensor ranges
# We narrow these slightly from physical limits (0-14) to detect rail-hitting (disconnected sensors)
PH_MIN, PH_MAX = 0.1, 13.9 
TDS_MIN = 0.0
TEMP_MIN_F, TEMP_MAX_F = 32.0, 120.0
HUMIDITY_MIN, HUMIDITY_MAX = 0.0, 100.0

# Audio threshold for pump detection (placeholder, needs calibration)
# We'll just return the noise level for now, but fail if it's absolute zero (broken mic)
NOISE_THRESHOLD_RMS = 0.01 

async def check_sensors() -> Dict[str, SensorCheckResult]:
    results = {}

    # 1. pH Check
    try:
        ph_val = ph_sensor.get_ph()
        passed = PH_MIN <= ph_val <= PH_MAX
        msg = "Normal" if passed else f"Out of bounds ({PH_MIN}-{PH_MAX})"
        results["ph"] = SensorCheckResult(passed=passed, value=ph_val, message=msg)
    except Exception as e:
        results["ph"] = SensorCheckResult(passed=False, value=0.0, message=f"Error: {str(e)}")

    # 2. TDS Check
    try:
        tds_val = tds_sensor.get_tds_ppm()
        passed = tds_val >= TDS_MIN
        msg = "Normal" if passed else "Negative value"
        results["tds"] = SensorCheckResult(passed=passed, value=tds_val, message=msg)
    except Exception as e:
        results["tds"] = SensorCheckResult(passed=False, value=0.0, message=f"Error: {str(e)}")

    # 3. DHT (Environment) Check
    try:
        env_data = dht_sensor.read()
        if "error" in env_data:
            results["environment"] = SensorCheckResult(
                passed=False, value=env_data, message=env_data["error"]
            )
        else:
            temp = env_data["temperature_f"]
            hum = env_data["humidity_percent"]
            passed_temp = TEMP_MIN_F <= temp <= TEMP_MAX_F
            passed_hum = HUMIDITY_MIN <= hum <= HUMIDITY_MAX
            
            if passed_temp and passed_hum:
                msg = "Normal"
                passed = True
            else:
                msg = f"Out of bounds (Temp: {temp}, Hum: {hum})"
                passed = False
                
            results["environment"] = SensorCheckResult(
                passed=passed, value=env_data, message=msg
            )
    except Exception as e:
        results["environment"] = SensorCheckResult(passed=False, value={}, message=f"Error: {str(e)}")

    return results

def analyze_audio(filename: str) -> float:
    """Calculates RMS amplitude of the wave file."""
    try:
        with wave.open(filename, 'rb') as wf:
            width = wf.getsampwidth()
            # We assume 16-bit audio from microphone.py (paInt16)
            if width != 2: 
                return 0.0
            
            frames = wf.readframes(wf.getnframes())
            # Convert to numpy array
            # Int16 range is -32768 to 32767
            data = np.frombuffer(frames, dtype=np.int16).astype(np.float32)
            
            if len(data) == 0:
                return 0.0
                
            # Normalize to -1.0 to 1.0
            data = data / 32768.0
            
            # Calculate RMS
            rms = np.sqrt(np.mean(data**2))
            return float(rms)
    except Exception:
        return 0.0
    finally:
        # Cleanup temp file
        if os.path.exists(filename):
            os.remove(filename)

async def check_pump() -> PumpCheckResult:
    """
    Selects a safe pump to test, runs it for 1s, and listens.
    """
    selected_pump: Optional[PumpID] = None
    
    # Logic to select a safe pump
    # Prefer Water In (Fill) if not full (safest, adds fresh water)
    if not water_level.is_full:
        selected_pump = PumpID.water_in
    # Else, prefer Water Out (Drain) if not empty (safest, removes water)
    elif not water_level.is_empty:
        selected_pump = PumpID.water_out
    
    if not selected_pump:
        return PumpCheckResult(
            passed=True, # Not a failure, just skipped
            noise_level=0.0,
            message="Skipped: Cannot safely run pumps (Tank state ambiguous or full/empty constraints)."
        )

    try:
        # Record concurrently with pump operation
        # We start recording slightly before pump and extend slightly after?
        # Actually, microphone.record_clip is blocking in its current implementation (bad for async).
        # However, looking at microphone.py, it uses standard blocking PyAudio/loop.
        # We need to run it in a thread or separate process to happen *while* the pump runs.
        # But wait, pump_controller.dispense is async (sleeps).
        
        # Plan:
        # 1. Start audio recording in a thread (since it's blocking).
        # 2. Wait 0.5s.
        # 3. Fire Pump for 1.0s.
        # 4. Recording finishes (e.g., 2.0s total).
        
        # Let's adjust duration. 
        TEST_DURATION = 1.0
        RECORD_DURATION = 2.0
        
        # We'll use asyncio.to_thread to run the blocking record
        record_task = asyncio.to_thread(microphone.record_clip, duration=RECORD_DURATION, filename="diag_pump.wav")
        
        # Delay pump start slightly so it happens during recording
        await asyncio.sleep(0.5)
        
        # Run Pump
        await pump_controller.dispense(selected_pump.value, TEST_DURATION)
        
        # Wait for recording to finish
        filename = await record_task
        
        # Analyze
        rms = analyze_audio(filename)
        
        passed = rms > NOISE_THRESHOLD_RMS
        msg = f"Pump {selected_pump.value} ran. Audio detected." if passed else f"Pump {selected_pump.value} ran, but little audio detected."
        
        return PumpCheckResult(
            passed=passed,
            pump_id=selected_pump.value,
            noise_level=rms,
            message=msg
        )

    except Exception as e:
        return PumpCheckResult(
            passed=False,
            pump_id=selected_pump.value if selected_pump else "unknown",
            noise_level=0.0,
            message=f"Error testing pump: {str(e)}"
        )

async def execute_diagnostic_check() -> DiagnosticResponse:
    # Run checks
    sensor_results = await check_sensors()
    pump_result = await check_pump()
    
    # Determine overall status
    status = "healthy"
    
    # If any sensor failed
    for res in sensor_results.values():
        if not res.passed:
            status = "warning" # Sensors out of range often just mean maintenance needed, not critical error
            
    # If pump failed
    if not pump_result.passed and "Skipped" not in pump_result.message:
        status = "error" # Pump failure is hardware issue
        
    return DiagnosticResponse(
        status=status,
        sensors=sensor_results,
        pumps=pump_result
    )
