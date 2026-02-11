import sys
import pytest
import pytest_asyncio
from unittest.mock import MagicMock, PropertyMock

# --- Hardware Mocking Setup ---
# We must mock these libraries BEFORE any src code is imported.

# 1. Mock gpiozero
mock_gpiozero = MagicMock()

class MockDigitalOutputDevice:
    def __init__(self, pin, *args, **kwargs):
        self.pin = pin
        self.value = False
        self._active_high = kwargs.get('active_high', True)

    def on(self):
        self.value = True

    def off(self):
        self.value = False
        
    @property
    def is_active(self):
        return self.value

class MockButton:
    def __init__(self, pin, *args, **kwargs):
        self.pin = pin
        self._is_pressed = False
        # pull_up logic is handled by the hardware, 
        # but for the mock we just care about the logical state.
    
    @property
    def is_pressed(self):
        return self._is_pressed
    
    @is_pressed.setter
    def is_pressed(self, value: bool):
        self._is_pressed = value

mock_gpiozero.DigitalOutputDevice = MockDigitalOutputDevice
mock_gpiozero.Button = MockButton
sys.modules["gpiozero"] = mock_gpiozero

# 2. Mock spidev
mock_spidev = MagicMock()
class MockSpiDev:
    def __init__(self):
        self.max_speed_hz = 0
    def open(self, bus, device): pass
    def close(self): pass
    def xfer2(self, data):
        # Return dummy data. 
        # MCP3008 returns [0, 0, 0] typically if nothing connected,
        # but we'll control this via the fixture.
        return [0, 0, 0]

mock_spidev.SpiDev = MockSpiDev
sys.modules["spidev"] = mock_spidev

# 3. Mock adafruit_dht
mock_dht = MagicMock()
class MockDHT11:
    def __init__(self, pin):
        self.pin = pin
        self._temperature = 25.0
        self._humidity = 50.0
    
    @property
    def temperature(self):
        return self._temperature
    
    @property
    def humidity(self):
        return self._humidity
    
    def exit(self): pass

mock_dht.DHT11 = MockDHT11
sys.modules["adafruit_dht"] = mock_dht
sys.modules["adafruit_circuitpython_dht"] = mock_dht

# 4. Mock board
mock_board = MagicMock()
mock_board.D25 = "GPIO25"
sys.modules["board"] = mock_board

# 5. Mock pyaudio
mock_pyaudio = MagicMock()
mock_pyaudio.paInt16 = 8
sys.modules["pyaudio"] = mock_pyaudio

# 6. Mock smbus2
sys.modules["smbus2"] = MagicMock()

# 7. Mock rpi.gpio (sometimes required by gpiozero internally)
sys.modules["rpi.gpio"] = MagicMock()
sys.modules["RPi"] = MagicMock()
sys.modules["RPi.GPIO"] = MagicMock()

# --- Fixtures ---

@pytest.fixture
def mock_hardware(monkeypatch):
    """
    Provides access to the mocked hardware objects to control their state during tests.
    Since we patched sys.modules, we need to grab the instances that the app created.
    """
    # Import the modules that use the hardware
    from src.actuators.pumps import pump_controller
    from src.sensors.float_switches import water_level
    from src.sensors.dht import dht_sensor
    from src.sensors.tds import tds_sensor
    from src.hardware.adc import adc_device
    
    class HardwareControl:
        def __init__(self):
            self.pumps = pump_controller
            self.water_level = water_level
            self.dht = dht_sensor
            self.tds = tds_sensor
            self.adc = adc_device
            self.adc_values = {}
            
            # Initialize default side_effect
            self.adc.read = MagicMock(side_effect=self._adc_side_effect)
            
            # Reset Sensor State to avoid pollution
            # Default: Tank is Empty (Full=False, Empty=True) to match startup? 
            # Or safe intermediate (False, False)?
            # Let's set to False, False (some water, but not full or empty triggers) 
            # to avoid immediate triggers in logic unless test specifies.
            self.set_water_level(full=False, empty=False)

        def _adc_side_effect(self, channel):
            return self.adc_values.get(channel, 0)

        def set_water_level(self, full: bool, empty: bool):
            # The 'is_pressed' property on the mock button is what we control.
            # Remember config: is_full = NOT full_switch.is_pressed (Active LOW logic essentially)
            # Logic in code: is_full property returns `not self.full_switch.is_pressed`
            
            # If we want is_full to be True, is_pressed must be False
            self.water_level.full_switch.is_pressed = not full
            
            # If we want is_empty to be True, is_pressed must be True
            self.water_level.empty_switch.is_pressed = empty

        def set_adc_value(self, channel: int, value: int):
            """Mock the ADC read method to return a specific value for a channel."""
            self.adc_values[channel] = value
            
    return HardwareControl()

@pytest.fixture
def client(mock_hardware):
    from fastapi.testclient import TestClient
    from src.main import app
    return TestClient(app)

@pytest_asyncio.fixture
async def async_client(mock_hardware):
    from httpx import AsyncClient, ASGITransport
    from src.main import app
    # Use ASGITransport to properly bind the app
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
