from src.sensors.float_switches import water_level

class TestWaterLevelSensors:
    def test_is_full_logic(self):
        # Case 1: Switch pressed (circuit closed) -> Not full (float is down)
        water_level.full_switch.is_pressed = True
        assert water_level.is_full is False
        
        # Case 2: Switch not pressed (circuit open) -> Full (float is up)
        water_level.full_switch.is_pressed = False
        assert water_level.is_full is True

    def test_is_empty_logic(self):
        # Case 1: Switch pressed -> Empty
        water_level.empty_switch.is_pressed = True
        assert water_level.is_empty is True
        
        # Case 2: Switch not pressed -> Not Empty
        water_level.empty_switch.is_pressed = False
        assert water_level.is_empty is False

    def test_get_status(self):
        water_level.full_switch.is_pressed = False # Full
        water_level.empty_switch.is_pressed = False # Not Empty
        
        status = water_level.get_status()
        assert status["full"] is True
        assert status["empty"] is False
