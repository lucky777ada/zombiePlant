import asyncio
from typing import Optional, Dict
from fastapi import HTTPException
from src.config import PUMP_CALIBRATION_ML_PER_SEC
from src.actuators.pumps import pump_controller
from src.actuators.ac_relay import ac_relay
from src.sensors.tds import tds_sensor
from src.models import NutrientRecipe, FeedResponse, DoseResponse, PumpID
from src.logic.common import empty_tank_logic, fill_to_max_logic

# Standard recipes (in mL) - Placeholder values, should be calibrated to tank size
RECIPES = {
    NutrientRecipe.vegetative: {"flora_micro": 4.0, "flora_gro": 5.0, "flora_bloom": 1.0},
    NutrientRecipe.flowering: {"flora_micro": 4.0, "flora_gro": 1.0, "flora_bloom": 5.0},
}

async def execute_feed_cycle(recipe: NutrientRecipe, custom_amounts: Optional[dict] = None) -> FeedResponse:
    # 1. Determine amounts
    if recipe == NutrientRecipe.custom:
        if not custom_amounts:
            raise HTTPException(status_code=400, detail="Custom recipe requires 'amounts_ml' parameter.")
        amounts = custom_amounts
    else:
        amounts = RECIPES[recipe]

    # Validate pumps exist for keys
    for key in amounts:
        if key not in ["flora_micro", "flora_gro", "flora_bloom"]:
            raise HTTPException(status_code=400, detail=f"Invalid nutrient type: {key}")

    # 2. Empty Tank
    await empty_tank_logic()

    # 3. Pre-Dose Nutrients (into empty tank)
    # Dose sequentially to avoid power spikes or interference, though parallel is likely fine.
    # Sequential is safer for precise timing.
    dispensed = {}
    for nutrient, amount in amounts.items():
        if amount > 0:
            duration = amount / PUMP_CALIBRATION_ML_PER_SEC
            
            await pump_controller.dispense(nutrient, duration)
            dispensed[nutrient] = amount

    # 4. Fill to Max (Turbulence mixes nutrients)
    await fill_to_max_logic()

    # 5. Mix (Air Stones via AC Relay)
    # Ensure AC is ON
    was_active = ac_relay.is_active
    if not was_active:
        ac_relay.turn_on()
    
    # Mix for 3 minutes
    MIX_DURATION = 180  # 3 minutes
    await asyncio.sleep(MIX_DURATION)

    # Restore AC state if it was off? 
    # The plan says "Ensure AC Relay is ON...". It doesn't explicitly say to turn it off.
    # Usually air stones should stay on for DWC. But if the user had it off, maybe they want it off?
    # Logic 4.1.3 in plan says "Turn on AC Relay (Light) if off... Restore previous" for Visual Check.
    # For Feed, it implies we want it mixed. I'll leave it ON if it's the air pump.
    # But wait, the AC Relay controls "Main Grow Light + Air Pump".
    # If we turn it on at night to mix, we flash the plant!
    # "Control: AC Relay (Controls Main Grow Light + Air Pump with 2 Air Stones)."
    # This is a hardware limitation.
    # If we feed at night, we wake the plant.
    # Users should schedule feeds during day cycle.
    # I will leave it in the state strictly required for mixing (ON), and maybe revert if it was OFF?
    # If I turn it off, the water goes stagnant. 
    # I'll assume for now we leave it as is (ON) because mixing is critical.
    # Or better: If it was OFF, turn it ON for mixing, then back OFF?
    # "The air stones... mix nutrients within ~3 minutes when the AC Relay is active."
    # If I turn it off after, I stop oxygenation.
    # I'll leave it ON implies "Mixing", but if I turn it off, I stop mixing.
    # I'll just ensure it's ON during the mix phase. 
    # I won't turn it off automatically to avoid killing the roots (they need air), 
    # unless the user explicitly manages the cycle elsewhere.
    # Actually, if the user requested a feed, they probably expect the system to run.
    # But if it's night?
    # I'll stick to the plan: "Mix: Ensure AC Relay is ON for at least 3 minutes".
    # I won't revert it.

    # 6. Verify (Check TDS)
    tds_ppm = tds_sensor.get_tds_ppm()
    
    # Optional: Logic to warn if TDS is too low (pump failure/empty bottle)?
    # For now, just return the value.

    return FeedResponse(
        message="Feed cycle complete",
        recipe=recipe,
        amounts_dispensed=dispensed,
        final_tds=tds_ppm
    )

async def execute_dose(nutrient: PumpID, amount_ml: float, mix_seconds: int = 30) -> DoseResponse:
    # Validate nutrient
    if nutrient.value not in ["flora_micro", "flora_gro", "flora_bloom"]:
        raise HTTPException(status_code=400, detail=f"Invalid nutrient pump: {nutrient}")

    # Calculate duration
    duration = amount_ml / PUMP_CALIBRATION_ML_PER_SEC
    
    # Dispense
    await pump_controller.dispense(nutrient.value, duration)
    
    # Mix
    was_active = ac_relay.is_active
    if not was_active:
        ac_relay.turn_on()
        
    await asyncio.sleep(mix_seconds)
    
    # Restore AC state if it was off (avoid leaving light on at night for a simple dose)
    # Also, reading TDS is often more accurate without active bubbles.
    if not was_active:
        ac_relay.turn_off()
        await asyncio.sleep(2) # Let bubbles settle

    # Read TDS
    tds_ppm = tds_sensor.get_tds_ppm()
    
    return DoseResponse(
        message="Dose complete",
        nutrient=nutrient,
        amount_dispensed_ml=amount_ml,
        final_tds=tds_ppm
    )
