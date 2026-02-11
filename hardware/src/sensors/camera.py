import subprocess
import os

class CameraManager:
    def __init__(self, output_dir="captures"):
        self.output_dir = output_dir
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)

    def capture_image(self, filename="latest.jpg", **kwargs):
        """
        Captures an image using rpicam-still.
        Accepts optional kwargs to tune the image:
        - shutter: Shutter speed in microseconds
        - gain: Analog gain
        - ev: Exposure compensation (e.g., -1.0 to 1.0)
        - metering: 'centre', 'spot', 'average'
        - saturation: 0.0 - 2.0 (default 1.0)
        - brightness: -1.0 - 1.0 (default 0.0)
        - contrast: 0.0 - 2.0 (default 1.0)
        """
        path = os.path.join(self.output_dir, filename)
        
        cmd = ["rpicam-still", "-o", path, "--immediate", "--nopreview"]
        
        # Map kwargs to command line flags
        if "shutter" in kwargs:
            cmd.extend(["--shutter", str(kwargs["shutter"])])
        if "gain" in kwargs:
            cmd.extend(["--gain", str(kwargs["gain"])])
        if "ev" in kwargs:
            cmd.extend(["--ev", str(kwargs["ev"])])
        if "metering" in kwargs:
            cmd.extend(["--metering", str(kwargs["metering"])])
        if "saturation" in kwargs:
            cmd.extend(["--saturation", str(kwargs["saturation"])])
        if "brightness" in kwargs:
            cmd.extend(["--brightness", str(kwargs["brightness"])])
        if "contrast" in kwargs:
            cmd.extend(["--contrast", str(kwargs["contrast"])])

        # Autofocus options for IMX708 and compatible cameras
        if "autofocus_mode" in kwargs:
            cmd.extend(["--autofocus-mode", str(kwargs["autofocus_mode"])])
        if "lens_position" in kwargs:
            cmd.extend(["--lens-position", str(kwargs["lens_position"])])
            
        # Resolution options
        if "width" in kwargs:
            cmd.extend(["--width", str(kwargs["width"])])
        if "height" in kwargs:
            cmd.extend(["--height", str(kwargs["height"])])

        try:
            # Using rpicam-still for Raspberry Pi 5 compatibility (Bookworm+)
            subprocess.run(cmd, check=True)
            return path
        except subprocess.CalledProcessError as e:
            return f"Error capturing image: {e}"

camera = CameraManager()
