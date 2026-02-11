from gpiozero import DigitalOutputDevice
from src.config import AC_RELAY_GPIO

class ACRelayController:
    def __init__(self):
        # Digital Loggers IoT Relay V2 is Active HIGH (3.3V turns Normally Off outlets ON)
        # initial_value=False ensures it starts in the OFF state.
        self.device = DigitalOutputDevice(AC_RELAY_GPIO, active_high=True, initial_value=False)

    def turn_on(self):
        """Activates the AC relay (Normally Off outlets turn ON)."""
        self.device.on()

    def turn_off(self):
        """Deactivates the AC relay (Normally Off outlets turn OFF)."""
        self.device.off()

    @property
    def is_active(self):
        """Returns the current state of the relay."""
        return self.device.is_active

ac_relay = ACRelayController()
