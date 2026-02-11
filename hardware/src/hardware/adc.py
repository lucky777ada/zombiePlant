import spidev

class MCP3008:
    def __init__(self, bus=0, device=0):
        self.spi = spidev.SpiDev()
        self.spi.open(bus, device)
        self.spi.max_speed_hz = 1350000

    def read(self, channel):
        if channel < 0 or channel > 7:
            return -1
        # MCP3008: Start bit, SGL/DIFF, D2, D1, D0
        # 1 = Start
        # 8 + channel << 4 provides the control bits
        adc = self.spi.xfer2([1, (8 + channel) << 4, 0])
        data = ((adc[1] & 3) << 8) + adc[2]
        return data

    def close(self):
        self.spi.close()

# Singleton instance
adc_device = MCP3008()
