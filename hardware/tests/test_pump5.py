from gpiozero import DigitalOutputDevice
import time

# PUMP_FLORA_BLOOM (INT5) is on GPIO 24
# active_high=False because standard relays are Active LOW
pump = DigitalOutputDevice(24, active_high=False, initial_value=False)

print("Starting Pump 5 (FloraBloom) for 5 seconds...")
pump.on()
time.sleep(5)
pump.off()
print("Pump 5 Stopped.")
