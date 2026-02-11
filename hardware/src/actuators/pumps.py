from gpiozero import DigitalOutputDevice
from src.config import (
    PUMP_WATER_OUT_GPIO, 
    PUMP_WATER_IN_GPIO,
    PUMP_FLORA_MICRO_GPIO,
    PUMP_FLORA_GRO_GPIO,
    PUMP_FLORA_BLOOM_GPIO
)
import asyncio

class PumpController:
    def __init__(self):
        # active_high=False assumes Active Low Relay (Standard for SunFounder)
        self.water_out = DigitalOutputDevice(PUMP_WATER_OUT_GPIO, active_high=False, initial_value=False)
        self.water_in = DigitalOutputDevice(PUMP_WATER_IN_GPIO, active_high=False, initial_value=False)
        self.flora_micro = DigitalOutputDevice(PUMP_FLORA_MICRO_GPIO, active_high=False, initial_value=False)
        self.flora_gro = DigitalOutputDevice(PUMP_FLORA_GRO_GPIO, active_high=False, initial_value=False)
        self.flora_bloom = DigitalOutputDevice(PUMP_FLORA_BLOOM_GPIO, active_high=False, initial_value=False)
        
        self.pumps = {
            "water_out": self.water_out,
            "water_in": self.water_in,
            "flora_micro": self.flora_micro,
            "flora_gro": self.flora_gro,
            "flora_bloom": self.flora_bloom
        }

    def activate_pump(self, pump_id: str):
        if pump_id not in self.pumps:
            raise ValueError(f"Pump {pump_id} not found.")
        self.pumps[pump_id].on()
        return True

    def deactivate_pump(self, pump_id: str):
         if pump_id not in self.pumps:
            raise ValueError(f"Pump {pump_id} not found.")
         self.pumps[pump_id].off()
         return True

    async def dispense(self, pump_id: str, duration: float):
        self.activate_pump(pump_id)
        await asyncio.sleep(duration)
        self.deactivate_pump(pump_id)

pump_controller = PumpController()