from fastapi import FastAPI, HTTPException, Body, Query
from contextlib import asynccontextmanager
from fastapi.responses import FileResponse, JSONResponse, HTMLResponse
import asyncio
import os
import datetime
from typing import Dict, Union, Literal

# Imports
from src.state import system_lock
from src.models import (
    PumpID, RelayState, PumpCommand, StatusResponse, SuccessResponse,
    PumpResponse, ACRelayResponse, WaterLevelStatus, TDSStatus, PHStatus,
    DHTSuccess, DHTError, HardwareStatusResponse, FillResponse,
    EmptyResponse, FlushResponse, ErrorResponse, CameraErrorResponse
)
from src.actuators.pumps import pump_controller
from src.actuators.ac_relay import ac_relay
from src.sensors.float_switches import water_level
from src.sensors.tds import tds_sensor
from src.sensors.ph import ph_sensor
from src.sensors.camera import camera
from src.sensors.microphone import microphone
from src.sensors.dht import dht_sensor

from src.logic.common import (
    fill_to_max_logic, empty_tank_logic, fix_overflow_logic, monitor_overflow_task
)
from src.logic.timelapse import timelapse_service
from src.routers import tools, jobs

@asynccontextmanager
async def lifespan(app: FastAPI):
    asyncio.create_task(monitor_overflow_task())
    asyncio.create_task(timelapse_service())
    yield

app = FastAPI(
    title="Autonomous Hydroponic Plant API",
    version="1.1",
    description="REST API to control and monitor the ZombiePlant V1.0 hydroponic system.",
    lifespan=lifespan
)

app.include_router(tools.router)
app.include_router(jobs.router)

@app.get("/", tags=["System"], response_model=StatusResponse)
def read_root():
    """Returns the current status and system version."""
    return {"status": "Online", "system": "ZombiePlant V1.1"}

@app.post("/control/pump", tags=["Control"], response_model=PumpResponse)
async def control_pump(command: PumpCommand = Body(...)):
    """Manually dispense from a specific pump for a given duration."""
    try:
        await pump_controller.dispense(command.pump_id.value, command.duration)
        return {"status": "success", "pump": command.pump_id, "duration": command.duration}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/control/ac_relay", tags=["Control"], response_model=ACRelayResponse)
async def control_ac_relay(state: RelayState = Query(..., description="The desired state for the AC power.")):
    """Turn AC Power 'on' or 'off' for devices like the main grow light."""
    if state == RelayState.on:
        ac_relay.turn_on()
    else:
        ac_relay.turn_off()
    return {"status": "success", "ac_power": state}

def _get_hardware_status_data():
    return {
        "pumps": {id: pump.value for id, pump in pump_controller.pumps.items()},
        "ac_power": "on" if ac_relay.is_active else "off",
        "water_level": water_level.get_status(),
        "tds": {
            "ppm": tds_sensor.get_tds_ppm(),
            "voltage": tds_sensor.read_voltage()
        },
        "ph": {
            "ph": ph_sensor.get_ph(),
            "voltage": ph_sensor.read_voltage()
        },
        "environment": dht_sensor.read()
    }

@app.get("/hardware/status", tags=["Status"], response_model=HardwareStatusResponse)
def hardware_status():
    """Retrieves the current status of all connected hardware."""
    return _get_hardware_status_data()

@app.get("/hardware/status/html", tags=["Status"], response_class=HTMLResponse)
def hardware_status_html():
    """Returns a self-refreshing HTML page with hardware status."""
    data = _get_hardware_status_data()
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    # Format environment
    env = data["environment"]
    if "error" in env:
        env_html = f"<span class='error'>{env['error']}</span>"
    else:
        env_html = f"{env['temperature_f']}°F | {env['humidity_percent']}% RH"

    # Format Water Level
    wl = data["water_level"]
    wl_status = "Normal"
    wl_class = "normal"
    if wl["full"]:
        wl_status = "FULL"
        wl_class = "success"
    elif wl["empty"]:
        wl_status = "EMPTY"
        wl_class = "danger"
    
    # Format Pumps
    pumps_html = ""
    for pid, state in data["pumps"].items():
        state_cls = "on" if state else "off"
        state_text = "ON" if state else "OFF"
        pumps_html += f"<div class='pump-item'><span class='pump-name'>{pid.replace('_', ' ').title()}</span><span class='status-badge {state_cls}'>{state_text}</span></div>"

    html_content = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>ZombiePlant Status</title>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #1a1a1a; color: #e0e0e0; margin: 0; padding: 20px; }}
            .container {{ max-width: 600px; margin: 0 auto; }}
            .card {{ background-color: #2d2d2d; border-radius: 8px; padding: 15px; margin-bottom: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }}
            h1 {{ font-size: 1.5rem; margin-top: 0; color: #4caf50; text-align: center; }}
            h2 {{ font-size: 1.1rem; border-bottom: 1px solid #444; padding-bottom: 5px; margin-top: 0; color: #aaa; }}
            .time {{ text-align: center; color: #888; font-size: 0.9rem; margin-bottom: 20px; display: flex; justify-content: center; align-items: center; gap: 10px; }}
            .row {{ display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }}
            .label {{ color: #888; }}
            .value {{ font-weight: bold; }}
            .success {{ color: #4caf50; }}
            .danger {{ color: #f44336; }}
            .normal {{ color: #2196f3; }}
            .error {{ color: #f44336; font-weight: bold; }}
            .on {{ background-color: #2e7d32; color: white; padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; }}
            .off {{ background-color: #424242; color: #aaa; padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; }}
            .pump-item {{ display: flex; justify-content: space-between; margin-bottom: 5px; border-bottom: 1px solid #333; padding: 5px 0; }}
            .pump-item:last-child {{ border-bottom: none; }}
            .pump-name {{ font-size: 0.9rem; }}
            .btn {{ cursor: pointer; border: none; font-size: 0.8rem; padding: 4px 8px; border-radius: 4px; color: white; }}
            .btn-pause {{ background-color: #555; }}
            .btn-pause:hover {{ background-color: #666; }}
            .btn-resume {{ background-color: #2196f3; }}
            .btn-resume:hover {{ background-color: #1976d2; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>ZombiePlant Status</h1>
            <div class="time">
                <span>Last Updated: {now}</span>
                <button id="toggleBtn" class="btn" onclick="toggleRefresh()">Pause</button>
            </div>
            
            <div class="card">
                <h2>Environment</h2>
                <div class="value" style="font-size: 1.2rem; text-align: center;">{env_html}</div>
            </div>

            <div class="card">
                <h2>Water System</h2>
                <div class="row"><span class="label">pH Level:</span> <span class="value">{data['ph']['ph']:.1f}</span></div>
                <div class="row"><span class="label">TDS (PPM):</span> <span class="value">{data['tds']['ppm']:.0f}</span></div>
                <div class="row"><span class="label">Tank Level:</span> <span class="value {wl_class}">{wl_status}</span></div>
            </div>

            <div class="card">
                <h2>Hardware Control</h2>
                <div class="row" style="margin-bottom: 15px; padding-bottom: 10px; border-bottom: 1px dashed #444;">
                    <span class="label">Main Light (AC):</span> 
                    <span class="status-badge {'on' if data['ac_power'] == 'on' else 'off'}">{data['ac_power'].upper()}</span>
                </div>
                <div style="font-size: 0.8rem; color: #666; margin-bottom: 5px; text-transform: uppercase; letter-spacing: 1px;">Pumps</div>
                {pumps_html}
            </div>
        </div>
        <script>
            const REFRESH_INTERVAL = 3000;
            let refreshTimer;

            function getPausedState() {{
                return localStorage.getItem('status_paused') === 'true';
            }}

            function setPausedState(paused) {{
                localStorage.setItem('status_paused', paused);
                updateButton();
            }}

            function updateButton() {{
                const btn = document.getElementById('toggleBtn');
                const isPaused = getPausedState();
                if (isPaused) {{
                    btn.textContent = "Resume";
                    btn.className = "btn btn-resume";
                }} else {{
                    btn.textContent = "Pause";
                    btn.className = "btn btn-pause";
                }}
            }}

            function toggleRefresh() {{
                const isPaused = getPausedState();
                if (isPaused) {{
                    setPausedState(false);
                    window.location.reload();
                }} else {{
                    setPausedState(true);
                    if (refreshTimer) clearTimeout(refreshTimer);
                }}
            }}

            updateButton();
            if (!getPausedState()) {{
                refreshTimer = setTimeout(() => window.location.reload(), REFRESH_INTERVAL);
            }}
        </script>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content, status_code=200)

@app.post("/control/fill_to_max", tags=["Control"], response_model=FillResponse)
async def fill_to_max():
    """
    Fills the tank to the maximum level and adjusts for overflow prevention.
    Activates Pump 2 (Water In) until the top float switch triggers,
    then briefly activates Pump 1 (Water Out) until the switch releases.
    """
    async with system_lock:
        return await fill_to_max_logic()

@app.post("/control/empty_tank", tags=["Control"], response_model=EmptyResponse)
async def empty_tank():
    """
    Empties the tank using the main water out pump.
    Activates Pump 1 (Water Out) until the bottom float switch indicates empty.
    """
    async with system_lock:
        return await empty_tank_logic()

@app.post("/control/system_flush", tags=["Control"], response_model=FlushResponse)
async def system_flush():
    """
    Performs a full system flush and nutrient pump priming cycle.
    1. Primes all 3 nutrient pumps.
    2. Fills the tank.
    3. Empties the tank.
    4. Fills the tank again.
    """
    async with system_lock:
        try:
            await pump_controller.dispense("flora_micro", 5)
            await pump_controller.dispense("flora_gro", 5)
            await pump_controller.dispense("flora_bloom", 5)
            await fill_to_max_logic()
            await empty_tank_logic()
            result = await fill_to_max_logic()
            
            return {
                "status": "success",
                "message": "System flush complete",
                "final_fill_details": result
            }

        except Exception as e:
            raise HTTPException(status_code=500, detail=f"System Flush Failed: {str(e)}")

@app.post("/control/fix_overflow", tags=["Control"], response_model=SuccessResponse)
async def fix_overflow_endpoint():
    """Manually triggers the overflow fix logic."""
    async with system_lock:
        await fix_overflow_logic()
        return {"status": "success"}

# --- Sensor Endpoints ---

@app.get("/sensors/ph", tags=["Sensors"], response_model=PHStatus)
def read_ph():
    """Reads the current pH level and raw voltage from the sensor."""
    return {
        "ph": ph_sensor.get_ph(),
        "voltage": ph_sensor.read_voltage()
    }

@app.get(
    "/sensors/camera/capture", 
    tags=["Sensors"],
    summary="Capture a standard photo",
    responses={
        200: {"content": {"image/jpeg": {}}},
        500: {"model": CameraErrorResponse}
    }
)
async def capture_photo(
    autofocus_mode: str = Query(None, description="Autofocus mode: default, manual, continuous"),
    lens_position: float = Query(None, description="Lens position for manual focus (0.0 - infinity)")
):
    """Captures an image with default camera settings."""
    kwargs = {}
    if autofocus_mode:
        kwargs['autofocus_mode'] = autofocus_mode
    if lens_position is not None:
        kwargs['lens_position'] = lens_position

    path = camera.capture_image(**kwargs)
    if os.path.exists(path):
        return FileResponse(path, media_type="image/jpeg")
    return JSONResponse(status_code=500, content={"error": "Capture failed"})

@app.get(
    "/sensors/camera/plant", 
    tags=["Sensors"],
    summary="Capture a photo with grow light ON",
    responses={
        200: {"content": {"image/jpeg": {}}},
        500: {"model": CameraErrorResponse}
    }
)
async def capture_plant_photo():
    """
    Captures an image with the main light (AC Relay) turned ON.
    Tuned for bright light: Lowers EV and Saturation.
    """
    was_active = ac_relay.is_active
    if not was_active:
        ac_relay.turn_on()
        await asyncio.sleep(2)
        
    try:
        path = camera.capture_image(filename="plant_latest.jpg", ev=-1.0, saturation=0.8, metering="average")
    finally:
        if not was_active:
            ac_relay.turn_off()

    if os.path.exists(path):
        return FileResponse(path, media_type="image/jpeg")
    return JSONResponse(status_code=500, content={"error": "Capture failed"})

@app.get(
    "/sensors/microphone/record", 
    tags=["Sensors"],
    summary="Record a short audio clip",
    responses={200: {"content": {"audio/wav": {}}}}
)
async def record_audio(
    duration: int = Query(5, gt=0, le=30, description="Recording duration in seconds.")
):
    """Records an audio clip, useful for detecting pump/system noises."""
    path = microphone.record_clip(duration=duration)
    return FileResponse(path, media_type="audio/wav")

@app.get(
    "/timelapse/latest",
    tags=["Timelapse"],
    summary="Get the latest timelapse video",
    responses={
        200: {"content": {"video/mp4": {}}},
        404: {"description": "No video found"}
    }
)
async def get_latest_timelapse():
    """
    Returns the latest generated timelapse video.
    """
    from glob import glob
    
    video_dir = "timeLapse/video"
    if not os.path.exists(video_dir):
        raise HTTPException(status_code=404, detail="Timelapse directory not found")
        
    videos = sorted(glob(os.path.join(video_dir, "*.mp4")))
    if not videos:
        raise HTTPException(status_code=404, detail="No timelapse videos found")
        
    # Return the last one (latest by filename date)
    latest_video = videos[-1]
    return FileResponse(latest_video, media_type="video/mp4")
