from gpiozero import DigitalOutputDevice
import time

# PUMP_WATER_OUT (INT1) is on GPIO 17
# active_high=False because standard relays are Active LOW
pump = DigitalOutputDevice(17, active_high=False, initial_value=False)

print("Starting Pump 1 (Water Out) for 25 seconds...")
pump.on()
time.sleep(25)
pump.off()
print("Pump 1 Stopped.")
