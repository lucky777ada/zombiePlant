# Pin Configuration

# Relay / Pumps
PUMP_WATER_OUT_GPIO = 17 # INT1
PUMP_WATER_IN_GPIO  = 27 # INT2
PUMP_FLORA_MICRO_GPIO = 22 # INT3
PUMP_FLORA_GRO_GPIO   = 23 # INT4
PUMP_FLORA_BLOOM_GPIO = 24 # INT5

# Float Switches (Vertical Stainless Steel)
FLOAT_SWITCH_FULL_GPIO = 5  # Top
FLOAT_SWITCH_EMPTY_GPIO = 6 # Bottom

# I2C (GY-906 IR Temp Sensor)
# SDA: GPIO 2
# SCL: GPIO 3

# SPI (MCP3008 for Analog Sensors: pH, TDS)
# CE0:  GPIO 8
# MISO: GPIO 9
# MOSI: GPIO 10
# SCLK: GPIO 11
# Channel 0: TDS Sensor
# Channel 1: pH Sensor

# DHT11 Temperature & Humidity
DHT11_GPIO = 25

# AC Power Relay (IoT Relay)
AC_RELAY_GPIO = 16

# Pump Calibration
# Default flow rate estimation (can be tuned per pump if needed later)
PUMP_CALIBRATION_ML_PER_SEC = 1.0
