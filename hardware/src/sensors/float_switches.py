from gpiozero import Button
from src.config import FLOAT_SWITCH_FULL_GPIO, FLOAT_SWITCH_EMPTY_GPIO

class WaterLevelSensors:
    def __init__(self):
        # pull_up=True means the pin is HIGH by default. 
        # The switch should connect the pin to GND when triggered.
        self.full_switch = Button(FLOAT_SWITCH_FULL_GPIO, pull_up=True)
        self.empty_switch = Button(FLOAT_SWITCH_EMPTY_GPIO, pull_up=True)

    @property
    def is_full(self) -> bool:
        """Returns True if the 'Full' (Top) switch is triggered (Inverted Logic)."""
        # Inverted: Returns True if the circuit is OPEN (not pressed)
        return not self.full_switch.is_pressed

    @property
    def is_empty(self) -> bool:
        """Returns True if the 'Empty' (Bottom) switch is triggered."""
        return self.empty_switch.is_pressed

    def get_status(self):
        return {
            "full": self.is_full,
            "empty": self.is_empty
        }

water_level = WaterLevelSensors()
