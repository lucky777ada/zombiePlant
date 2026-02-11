from src.hardware.adc import adc_device

class PHSensor:
    def __init__(self, channel=1):
        self.channel = channel
        self.v_ref = 3.3  # System voltage (matches ADC VREF)
        # Calibration constants (Ideal theoretical values)
        # Neutral voltage is typically VCC/2 (2.5V for 5V supply), but often offset.
        # Needs manual calibration: y = mx + c
        # m (slope) is typically negative for pH sensors (-59.16mV/pH at 25C).
        # We start with generic values that can be tuned.
        self.calibration_value = 0.0  # Offset adjustment
        
    def read_voltage(self):
        """
        Reads the voltage with noise filtering.
        Takes 20 samples, sorts them, removes top/bottom outliers, 
        and averages the remaining middle values.
        """
        samples = []
        import time
        
        # Take 20 samples with a tiny delay
        for _ in range(20):
            raw = adc_device.read(self.channel)
            if raw != -1:
                samples.append(raw)
            time.sleep(0.005) # 5ms delay
            
        if not samples:
            raise ValueError("Failed to read from ADC")
            
        # Sort to easily find outliers
        samples.sort()
        
        # Discard the top 2 and bottom 2 (Outlier removal)
        if len(samples) > 4:
            samples = samples[2:-2]
            
        # Average the remaining
        avg_raw = sum(samples) / len(samples)
        
        voltage = (avg_raw / 1023.0) * self.v_ref
        return round(voltage, 3)

    def get_ph(self, temperature=25):
        """
        Calculate pH value based on calibrated hardware.
        Neutral (pH 7.0) = 2.5V
        """
        voltage = self.read_voltage()
        
        # Calibration Constants
        # Calibrated 2026-01-17 using 3-point buffer solution:
        # 9.18 pH @ 1.278V | 6.86 pH @ 1.678V | 4.01 pH @ 2.171V
        # Calculated Neutral (pH 7.0) is approx 1.65V
        neutral_voltage = 1.65
        
        # Calculated Sensitivity: ~0.173V per pH unit
        volts_per_ph = 0.173 
        
        ph_value = 7.0 + ((neutral_voltage - voltage) / volts_per_ph)
        
        return max(0.0, min(14.0, round(ph_value, 2)))

ph_sensor = PHSensor(channel=1)
