from gpiozero import DigitalOutputDevice
import time

# PUMP_FLORA_GRO (INT4) is on GPIO 23
# active_high=False because standard relays are Active LOW
pump = DigitalOutputDevice(23, active_high=False, initial_value=False)

print("Starting Pump 4 (FloraGro) for 5 seconds...")
pump.on()
time.sleep(5)
pump.off()
print("Pump 4 Stopped.")
