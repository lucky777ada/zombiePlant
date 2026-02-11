import pytest
import asyncio
from unittest.mock import AsyncMock, patch
from src.actuators.pumps import pump_controller

class TestPumpController:
    def test_activate_pump_valid(self):
        assert pump_controller.activate_pump("water_out") is True
        assert pump_controller.pumps["water_out"].value is True

    def test_deactivate_pump_valid(self):
        pump_controller.activate_pump("water_out")
        assert pump_controller.deactivate_pump("water_out") is True
        assert pump_controller.pumps["water_out"].value is False

    def test_invalid_pump_id(self):
        with pytest.raises(ValueError):
            pump_controller.activate_pump("invalid_pump")

    @pytest.mark.asyncio
    async def test_dispense(self):
        # Patch asyncio.sleep so we don't actually wait
        with patch('asyncio.sleep', new_callable=AsyncMock) as mock_sleep:
            await pump_controller.dispense("water_in", 5.0)
            
            # Verify pump turned on
            # Since dispense turns it off at the end, we check the flow
            # We can check if sleep was called with correct duration
            mock_sleep.assert_awaited_once_with(5.0)
            
            # The pump should be off at the end
            assert pump_controller.pumps["water_in"].value is False
