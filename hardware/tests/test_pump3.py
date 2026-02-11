from gpiozero import DigitalOutputDevice
import time

# PUMP_FLORA_MICRO (INT3) is on GPIO 22
# active_high=False because standard relays are Active LOW
pump = DigitalOutputDevice(22, active_high=False, initial_value=False)

print("Starting Pump 3 (FloraMicro) for 5 seconds...")
pump.on()
time.sleep(5)
pump.off()
print("Pump 3 Stopped.")
