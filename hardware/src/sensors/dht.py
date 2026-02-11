import adafruit_dht
import board
from src.config import DHT11_GPIO

class DHTSensor:
    def __init__(self):
        # We need to dynamically get the board pin. 
        # board.D25 is the object for GPIO 25.
        # Since we stored 25 in config, we can map it or just hardcode if we are careful.
        # Constructing the attribute name: 'D' + str(GPIO)
        pin_attr = f"D{DHT11_GPIO}"
        if hasattr(board, pin_attr):
            self.pin = getattr(board, pin_attr)
        else:
            raise ValueError(f"Pin GPIO {DHT11_GPIO} is not defined in 'board' module.")
        
        # Initialize the DHT11 device
        self.dht_device = adafruit_dht.DHT11(self.pin)

    def read(self):
        """
        Returns a dictionary with temperature (C) and humidity (%).
        DHT sensors can be flaky, so we handle errors gracefully.
        """
        try:
            temperature = self.dht_device.temperature
            humidity = self.dht_device.humidity
            
            # Sometimes it returns None on read error
            if temperature is None or humidity is None:
                 return {"error": "Sensor read returned None"}

            temperature_f = (temperature * 9/5) + 32

            return {
                "temperature_f": round(temperature_f, 1),
                "humidity_percent": humidity
            }
        except RuntimeError as error:
            # Common error: "DHT sensor not found, check wiring" or checksum error
            return {"error": str(error)}
        except Exception as error:
            return {"error": f"Critical DHT error: {error}"}

    def cleanup(self):
        self.dht_device.exit()

dht_sensor = DHTSensor()
