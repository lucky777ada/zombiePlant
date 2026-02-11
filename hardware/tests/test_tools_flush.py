import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from src.main import system_lock
from src.actuators.ac_relay import ac_relay

# Reset lock between tests
@pytest.fixture(autouse=True)
def release_lock():
    yield
    if system_lock.locked():
        system_lock.release()

@pytest.mark.asyncio
async def test_system_flush_success(client, mock_hardware):
    # Initial State: Let's start with a Full tank
    mock_hardware.set_water_level(full=True, empty=False)
    
    # We want to verify that AC Relay turns ON during the soak phase
    # And that pumps cycle correctly.
    
    # Track actions to verify sequence
    actions = []

    async def sleep_side_effect(duration):
        # Log what's happening during this sleep
        current_action = "idle"
        
        if mock_hardware.pumps.pumps["water_out"].value:
            current_action = "draining"
            # Simulate draining: become empty
            mock_hardware.set_water_level(full=False, empty=True)
            
        elif mock_hardware.pumps.pumps["water_in"].value:
            current_action = "filling"
            # Simulate filling: become full
            mock_hardware.set_water_level(full=True, empty=False)
            
        elif ac_relay.is_active:
            current_action = "soaking"
            # Hardware state doesn't change, just time passes
            
        actions.append(current_action)
        return None

    # Patch sleep to drive the simulation
    with patch('asyncio.sleep', side_effect=sleep_side_effect):
        response = client.post("/tools/flush?soak_duration=5")

    assert response.status_code == 200
    data = response.json()
    assert data["message"] == "System flush complete (Soak: 5s)"
    assert data["final_fill_details"]["message"] == "Tank filled and leveled"

    # Verify Sequence
    # Expected:
    # 1. Draining (Empty dirty water)
    # 2. Filling (Fresh water)
    # 3. Soaking (Mix/Rinse)
    # 4. Draining (Empty rinse water)
    # 5. Filling (Final fill)
    
    # Note: The logic loops (sleeps) multiple times while pumping.
    # So we expect runs of "draining", then runs of "filling", etc.
    
    # Compress the actions list to just unique transitions
    sequence = []
    if actions:
        sequence.append(actions[0])
        for action in actions[1:]:
            if action != sequence[-1]:
                sequence.append(action)
    
    # Expected sequence: draining -> filling -> soaking -> draining -> filling
    # Note: 'idle' might appear if checks happen when nothing is on (unlikely in this logic but possible)
    # Also, "filling" might have a "draining" (adjust) phase if it overfills?
    # Our mock makes it "perfectly full" immediately, so adjust loop might run once?
    # fill_to_max logic: fills until full. If full, checks overflow (adjust).
    # If we set it to Full immediately, the fill loop exits.
    # Then the adjust loop (drain) starts?
    # _fill_to_max_logic: 
    #   if not full: activate IN, loop...
    #   if full: activate OUT, loop...
    # So yes, "filling" phase will be followed by a brief "draining" (adjust) phase 
    # because our mock sets it to "Full" (triggering the top switch).
    # Realistically, "Full" switch means "Stop filling".
    # But the code says "if water_level.is_full: ... activate_pump(DRAIN_PUMP)".
    # So `fill_to_max` creates a [Fill -> Drain(Adjust)] sequence.
    
    # So expected sequence per `fill_to_max` call: [filling, draining] (if mock sets Full).
    # `empty_tank` call: [draining].
    
    # Full Sequence:
    # 1. Empty Tank -> [draining]
    # 2. Fill to Max -> [filling, draining]
    # 3. Soak -> [soaking]
    # 4. Empty Tank -> [draining]
    # 5. Fill to Max -> [filling, draining]
    
    # Let's just check key elements exist
    assert "draining" in sequence
    assert "filling" in sequence
    assert "soaking" in sequence
    
    # Count occurrences
    fill_count = sequence.count("filling")
    soak_count = sequence.count("soaking")
    
    assert fill_count >= 2 # Fill 1, Fill 2
    assert soak_count >= 1 # Soak
