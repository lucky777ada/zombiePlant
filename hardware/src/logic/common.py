import asyncio
from fastapi import HTTPException
from src.actuators.pumps import pump_controller
from src.sensors.float_switches import water_level

async def fill_to_max_logic():
    """Internal logic to fill the tank to max and adjust."""
    FILL_PUMP = "water_in"
    DRAIN_PUMP = "water_out"
    MAX_DURATION = 280
    ADJUST_TIMEOUT = 200
    CHECK_INTERVAL = 0.5
    
    fill_duration = 0
    adjust_duration = 0

    if not water_level.is_full:
        try:
            pump_controller.activate_pump(FILL_PUMP)
            while not water_level.is_full and fill_duration < MAX_DURATION:
                await asyncio.sleep(CHECK_INTERVAL)
                fill_duration += CHECK_INTERVAL
            pump_controller.deactivate_pump(FILL_PUMP)
            
            if fill_duration >= MAX_DURATION:
                raise HTTPException(status_code=500, detail="Fill timed out")
        except Exception as e:
            pump_controller.deactivate_pump(FILL_PUMP)
            if isinstance(e, HTTPException): raise e
            raise HTTPException(status_code=500, detail=f"Fill Error: {str(e)}")

    if water_level.is_full:
        try:
            pump_controller.activate_pump(DRAIN_PUMP)
            while water_level.is_full and adjust_duration < ADJUST_TIMEOUT:
                await asyncio.sleep(CHECK_INTERVAL)
                adjust_duration += CHECK_INTERVAL
            pump_controller.deactivate_pump(DRAIN_PUMP)

            if adjust_duration >= ADJUST_TIMEOUT:
                 raise HTTPException(status_code=500, detail="Adjustment Error: Could not lower water level below sensor (Sensor stuck or tank severely overfilled?)")
                 
        except Exception as e:
            pump_controller.deactivate_pump(DRAIN_PUMP)
            if isinstance(e, HTTPException): raise e
            raise HTTPException(status_code=500, detail=f"Adjustment Error: {str(e)}")

    return {
        "status": "success", 
        "message": "Tank filled and leveled", 
        "fill_duration": round(fill_duration, 2), 
        "adjust_duration": round(adjust_duration, 2)
    }

async def empty_tank_logic():
    """Internal logic to empty the tank."""
    PUMP_ID = "water_out"
    MAX_DURATION = 280
    CHECK_INTERVAL = 0.5

    if water_level.is_full and water_level.is_empty:
        raise HTTPException(status_code=500, detail="Sensor Failure: Tank reports BOTH Full and Empty.")

    if water_level.is_empty:
        return {"status": "success", "message": "Tank already empty", "duration": 0}

    try:
        pump_controller.activate_pump(PUMP_ID)
        elapsed = 0
        while not water_level.is_empty and elapsed < MAX_DURATION:
            await asyncio.sleep(CHECK_INTERVAL)
            elapsed += CHECK_INTERVAL
        pump_controller.deactivate_pump(PUMP_ID)
        
        if elapsed >= MAX_DURATION:
             raise HTTPException(status_code=500, detail=f"Pump stopped after {MAX_DURATION}s safety limit")
        
        return {"status": "success", "message": "Tank emptied", "duration": round(elapsed, 2)}

    except Exception as e:
        pump_controller.deactivate_pump(PUMP_ID)
        if isinstance(e, HTTPException): raise e
        raise HTTPException(status_code=500, detail=str(e))

async def fix_overflow_logic():
    """Internal logic to fix overflow."""
    DRAIN_PUMP = "water_out"
    ADJUST_TIMEOUT = 60 # Short timeout for safety check
    CHECK_INTERVAL = 0.5
    adjust_duration = 0

    if water_level.is_full:
        try:
            pump_controller.activate_pump(DRAIN_PUMP)
            while water_level.is_full and adjust_duration < ADJUST_TIMEOUT:
                await asyncio.sleep(CHECK_INTERVAL)
                adjust_duration += CHECK_INTERVAL
            pump_controller.deactivate_pump(DRAIN_PUMP)
        except Exception:
            pump_controller.deactivate_pump(DRAIN_PUMP)
            # Log error but don't crash background task
            print("Error during overflow fix")
            
from src.state import system_lock

async def monitor_overflow_task():
    """Background task to monitor and fix overflow every 5 seconds."""
    while True:
        try:
            # Try to acquire lock, if busy (e.g. filling), skip this check
            if not system_lock.locked():
                async with system_lock:
                    if water_level.is_full:
                        print("Overflow detected by monitor. Fixing...")
                        await fix_overflow_logic()
        except Exception as e:
            print(f"Monitor Task Error: {e}")
        await asyncio.sleep(5)

