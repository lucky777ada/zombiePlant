from unittest.mock import MagicMock, patch
from src.models import NutrientRecipe

def test_feed_cycle_vegetative(client, mock_hardware):
    # Setup: Start with empty tank? Or full? 
    # Logic: 1. Empty (wait for empty), 2. Dose, 3. Fill (wait for full), 4. Mix, 5. Verify
    
    # We need to simulate the state transitions.
    # Logic 1: Empty Tank. Loops until is_empty is True.
    # Logic 2: Fill Tank. Loops until is_full is True.
    
    # We can mock asyncio.sleep to be instant to speed up tests, 
    # AND use side effects to change sensor states.
    
    # Mocking asyncio.sleep is tricky with TestClient. 
    # Instead, let's mock the pump activation to trigger the sensor change immediately.
    # When 'water_out' is activated (Empty logic), set empty=True.
    # When 'water_in' is activated (Fill logic), set full=True.
    
    original_activate = mock_hardware.pumps.activate_pump
    
    def side_effect_activate(pump_id):
        if pump_id == "water_out":
            # Empties the tank
            mock_hardware.set_water_level(full=False, empty=True)
        elif pump_id == "water_in":
            # Fills the tank
            mock_hardware.set_water_level(full=True, empty=False)
        return original_activate(pump_id)
        
    with patch.object(mock_hardware.pumps, 'activate_pump', side_effect=side_effect_activate):
        # Also need to mock dispense to NOT sleep for seconds
        # pump_controller.dispense calls activate, sleeps, deactivate.
        # We can patch dispense to avoid the sleep.
        # But wait, dispense is async.
        
        # Ideally we patch src.actuators.pumps.asyncio.sleep
        with patch("src.actuators.pumps.asyncio.sleep", return_value=None):
             # Also patch logic sleep
             with patch("src.logic.common.asyncio.sleep", return_value=None):
                 with patch("src.logic.feed.asyncio.sleep", return_value=None):
                    # Trigger request
                    response = client.post("/tools/feed", json={"recipe": "vegetative"})
                
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["recipe"] == "vegetative"
    
    # Verify pumps were called
    # Veg recipe: Micro 4, Gro 5, Bloom 1
    # We can check the mock calls if we want, but the response also confirms it.
    assert data["amounts_dispensed"]["flora_micro"] == 4.0
    assert data["amounts_dispensed"]["flora_gro"] == 5.0

def test_feed_cycle_custom_missing_amounts(client):
    response = client.post("/tools/feed", json={"recipe": "custom"})
    assert response.status_code == 400
    assert "Custom recipe requires 'amounts_ml'" in response.json()["detail"]

def test_feed_cycle_custom_valid(client, mock_hardware):
    # Same mocking setup
    original_activate = mock_hardware.pumps.activate_pump
    def side_effect_activate(pump_id):
        if pump_id == "water_out":
            mock_hardware.set_water_level(full=False, empty=True)
        elif pump_id == "water_in":
            mock_hardware.set_water_level(full=True, empty=False)
        return original_activate(pump_id)
    
    with patch.object(mock_hardware.pumps, 'activate_pump', side_effect=side_effect_activate):
        with patch("src.actuators.pumps.asyncio.sleep", return_value=None):
             with patch("src.logic.common.asyncio.sleep", return_value=None):
                 with patch("src.logic.feed.asyncio.sleep", return_value=None):
                    response = client.post("/tools/feed", json={
                        "recipe": "custom",
                        "amounts_ml": {"flora_micro": 10.0, "flora_gro": 0, "flora_bloom": 0}
                    })
                
    assert response.status_code == 200
    data = response.json()
    assert data["recipe"] == "custom"
    assert data["amounts_dispensed"]["flora_micro"] == 10.0
