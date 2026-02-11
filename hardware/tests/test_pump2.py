from gpiozero import DigitalOutputDevice
import time

# PUMP_WATER_IN (INT2) is on GPIO 27
# active_high=False because standard relays are Active LOW
pump = DigitalOutputDevice(27, active_high=False, initial_value=False)

print("Starting Pump 2 (Water In) for 25 seconds...")
pump.on()
time.sleep(25)
pump.off()
print("Pump 2 Stopped.")
