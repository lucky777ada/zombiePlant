from fastapi import APIRouter, Body, HTTPException, Query
from src.models import FeedRequest, FeedResponse, FlushResponse
from src.logic.feed import execute_feed_cycle, execute_dose
from src.logic.flush import execute_system_flush
from src.logic.diagnose import execute_diagnostic_check
from src.models import (
    FeedRequest, FeedResponse, FlushResponse, DiagnosticResponse, DoseRequest, DoseResponse
)
from src.state import system_lock # Import lock from main to ensure exclusivity

router = APIRouter(prefix="/tools", tags=["Tools"])

@router.post("/feed", response_model=FeedResponse)
async def smart_feed_cycle(request: FeedRequest = Body(...)):
    """
    Performs a complete drain-refill-mix cycle with nutrient dosing.
    1. Empty Tank.
    2. Dose Nutrients (Micro/Gro/Bloom).
    3. Fill to Max with fresh water.
    4. Mix (Air Stones/Light ON) for 3 minutes.
    5. Verify TDS.
    """
    # Use the global system lock to prevent concurrent hardware access
    if system_lock.locked():
        raise HTTPException(status_code=409, detail="System is busy with another operation.")
        
    async with system_lock:
        return await execute_feed_cycle(request.recipe, request.amounts_ml)

@router.post("/dose", response_model=DoseResponse)
async def smart_dose(request: DoseRequest = Body(...)):
    """
    Precise nutrient addition without draining the tank.
    1. Dispenses specific nutrient amount.
    2. Mixes (Air Stones) for specified duration.
    3. Returns new TDS reading.
    """
    if system_lock.locked():
        raise HTTPException(status_code=409, detail="System is busy with another operation.")

    async with system_lock:
        return await execute_dose(request.nutrient, request.amount_ml, request.mix_seconds)

@router.post("/flush", response_model=FlushResponse)
async def true_system_flush(
    soak_duration: int = Query(180, description="Duration in seconds to run the air stones/mix cycle.", ge=0)
):
    """
    Performs a full system flush (Rinse Cycle).
    1. Empty Tank.
    2. Fill to Max (Fresh Water).
    3. Soak/Mix (Air Stones ON) for X seconds.
    4. Empty Tank (Drain Rinse).
    5. Fill to Max (Fresh Water).
    """
    if system_lock.locked():
        raise HTTPException(status_code=409, detail="System is busy with another operation.")

    async with system_lock:
        return await execute_system_flush(soak_duration)

@router.post("/diagnose", response_model=DiagnosticResponse)
async def diagnostic_self_check():
    """
    Performs a daily health check for the hardware.
    1. Checks Sensor bounds (pH, TDS, Environment).
    2. Runs a pump briefly and verifies operation via Microphone analysis.
    3. Returns a structured health report.
    """
    if system_lock.locked():
        raise HTTPException(status_code=409, detail="System is busy with another operation.")

    async with system_lock:
        return await execute_diagnostic_check()
