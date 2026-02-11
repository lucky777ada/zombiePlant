from src.hardware.adc import adc_device

class TDSSensor:
    def __init__(self, channel=0):
        self.channel = channel
        self.v_ref = 3.3  # System voltage (usually 3.3V or 5V depending on ADC VREF)

    def read_voltage(self):
        raw = adc_device.read(self.channel)
        voltage = (raw / 1023.0) * self.v_ref
        return voltage

    def get_tds_ppm(self, temperature=25):
        """
        Calculate TDS in PPM (Parts Per Million).
        Includes basic temperature compensation.
        Formula based on standard analog TDS sensors.
        """
        voltage = self.read_voltage()
        
        # Temperature Compensation Coefficient
        compensation_coefficient = 1.0 + 0.02 * (temperature - 25.0)
        compensation_voltage = voltage / compensation_coefficient
        
        # Convert voltage to TDS value
        tds_value = (133.42 * compensation_voltage**3 
                     - 255.86 * compensation_voltage**2 
                     + 857.39 * compensation_voltage) * 0.5
        
        return max(0, round(tds_value, 2))

# Assuming TDS is connected to ADC Channel 0
tds_sensor = TDSSensor(channel=0)
