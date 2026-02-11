import asyncio
from src.actuators.ac_relay import ac_relay
from src.models import FlushResponse
from src.logic.common import empty_tank_logic, fill_to_max_logic

async def execute_system_flush(soak_duration: int = 180) -> FlushResponse:
    # 1. Empty Tank (Drain dirty/old water)
    await empty_tank_logic()

    # 2. Fill to Max (Fresh water)
    await fill_to_max_logic()

    # 3. Soak/Mix: Turn on AC Relay (Air Stones) for X minutes
    # This circulates fresh water through roots to remove salts.
    was_active = ac_relay.is_active
    if not was_active:
        ac_relay.turn_on()
    
    # Wait for the soak duration
    await asyncio.sleep(soak_duration)

    # Restore AC state (If it was OFF, turn it back OFF to avoid keeping light/air on if not desired)
    # Note: If it was ON, we leave it ON.
    if not was_active:
        ac_relay.turn_off()

    # 4. Empty Tank (Drain the rinse water)
    await empty_tank_logic()

    # 5. Fill to Max (Final fresh water fill)
    final_fill = await fill_to_max_logic()

    return FlushResponse(
        message=f"System flush complete (Soak: {soak_duration}s)",
        final_fill_details=final_fill
    )
