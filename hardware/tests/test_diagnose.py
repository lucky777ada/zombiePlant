import pytest
from unittest.mock import MagicMock, patch, mock_open
import numpy as np
from src.models import DiagnosticResponse

# --- Mock Wave Data ---
def generate_wave_data(rms_amplitude: float, duration_sec: int = 1, rate: int = 44100):
    """Generates bytes representing a sine wave with specific RMS."""
    t = np.linspace(0, duration_sec, int(rate * duration_sec), False)
    # Peak amplitude for desired RMS (Sine wave RMS = Peak / sqrt(2)) => Peak = RMS * sqrt(2)
    # Scaled to int16 range (32767)
    peak = rms_amplitude * np.sqrt(2) * 32767
    tone = peak * np.sin(2 * np.pi * 440 * t) # 440Hz tone
    return tone.astype(np.int16).tobytes()

@pytest.fixture
def mock_audio(monkeypatch):
    """Mocks wave.open and microphone recording."""
    
    # Mock Microphone to return a fake filename
    with patch("src.logic.diagnose.microphone") as mock_mic:
        mock_mic.record_clip.return_value = "mock_diag.wav"
        
        # Mock wave.open to simulate file reading
        # We need to simulate the context manager and readframes
        mock_wave = MagicMock()
        mock_wave_file = MagicMock()
        mock_wave.open.return_value.__enter__.return_value = mock_wave_file
        
        # Default behavior: No noise
        mock_wave_file.getsampwidth.return_value = 2
        mock_wave_file.getnframes.return_value = 44100
        mock_wave_file.readframes.return_value = generate_wave_data(0.0) # Silence
        
        # Patch the wave module in src.logic.diagnose
        with patch("src.logic.diagnose.wave", mock_wave):
            yield mock_mic, mock_wave_file

@pytest.mark.asyncio
async def test_diagnose_healthy(client, mock_hardware, mock_audio):
    mock_mic, mock_wave_file = mock_audio
    
    # 1. Setup Healthy Sensors
    mock_hardware.set_water_level(full=False, empty=True) # Empty -> Safe to fill (water_in)
    
    # Setup ADC for pH 7.0 (approx 1.65V)
    # 1.65V / 3.3V * 1023 ~= 511
    mock_hardware.set_adc_value(channel=1, value=511)
    
    # Setup ADC for TDS > 0
    mock_hardware.set_adc_value(channel=0, value=100)
    
    # Setup DHT
    mock_hardware.dht._temperature = 25.0 # 77F
    mock_hardware.dht._humidity = 50.0
    
    # 2. Setup Audio to detect Noise (Pump running)
    # Threshold is 0.01, so we give 0.1
    noise_data = generate_wave_data(0.1)
    mock_wave_file.readframes.return_value = noise_data
    
    # 3. Call Endpoint
    response = client.post("/tools/diagnose")
    assert response.status_code == 200
    data = response.json()
    
    assert data["status"] == "healthy"
    assert data["sensors"]["ph"]["passed"] is True
    assert data["pumps"]["passed"] is True
    assert data["pumps"]["pump_id"] == "water_in"
    assert data["pumps"]["noise_level"] > 0.01

@pytest.mark.asyncio
async def test_diagnose_pump_failure_silence(client, mock_hardware, mock_audio):
    mock_mic, mock_wave_file = mock_audio
    
    # Empty tank -> water_in selected
    mock_hardware.set_water_level(full=False, empty=True)
    
    # Silence
    mock_wave_file.readframes.return_value = generate_wave_data(0.0)
    
    response = client.post("/tools/diagnose")
    data = response.json()
    
    assert data["status"] == "error"
    assert data["pumps"]["passed"] is False
    assert data["pumps"]["pump_id"] == "water_in"
    assert "little audio detected" in data["pumps"]["message"]

@pytest.mark.asyncio
async def test_diagnose_sensor_warning(client, mock_hardware, mock_audio):
    mock_mic, mock_wave_file = mock_audio
    
    # Pump works
    mock_wave_file.readframes.return_value = generate_wave_data(0.1)
    
    # pH sensor out of bounds (ADC = 0 -> High pH or Low pH depending on calibration, likely extreme)
    mock_hardware.set_adc_value(channel=1, value=0) 
    
    response = client.post("/tools/diagnose")
    data = response.json()
    
    assert data["status"] == "warning"
    assert data["sensors"]["ph"]["passed"] is False

@pytest.mark.asyncio
async def test_diagnose_skip_pump(client, mock_hardware, mock_audio):
    # Both Full and Empty? (Impossible physical state, or just unsafe intermediate)
    # Let's say NOT Full and NOT Empty -> Middle
    # Our logic:
    # if not full -> water_in
    # else if not empty -> water_out
    
    # To trigger skip, we need a case where we can't do either?
    # Actually, logic is:
    # if not full -> water_in. (Safe to fill)
    # elif not empty -> water_out. (Safe to empty)
    
    # So if it IS FULL (can't fill) AND IS EMPTY (can't empty).
    # This state implies the sensors are contradictory (Tank is full and empty at same time).
    mock_hardware.set_water_level(full=True, empty=True)
    
    response = client.post("/tools/diagnose")
    data = response.json()
    
    assert data["pumps"]["passed"] is True # Skipped is not a fail
    assert "Skipped" in data["pumps"]["message"]
