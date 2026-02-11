import asyncio
import os
import time
import subprocess
import logging
from datetime import datetime
from glob import glob

from src.sensors.camera import camera
from src.actuators.ac_relay import ac_relay
from src.state import system_lock

logger = logging.getLogger("timelapse")

TIMELAPSE_DIR = "timeLapse"
IMAGES_DIR = os.path.join(TIMELAPSE_DIR, "images")
VIDEO_DIR = os.path.join(TIMELAPSE_DIR, "video")

# Ensure directories exist
os.makedirs(IMAGES_DIR, exist_ok=True)
os.makedirs(VIDEO_DIR, exist_ok=True)

async def capture_timelapse_image():
    """
    Captures an image for the timelapse.
    Ensures the grow light is ON during capture for consistency.
    """
    timestamp = int(time.time() * 1000)
    filename = f"{timestamp}.jpg"
    
    # We need to ensure light is on, similar to capture_plant_photo
    async with system_lock:
        was_active = ac_relay.is_active
        
        try:
            if not was_active:
                ac_relay.turn_on()
                # Give light a moment to stabilize/warm up if needed, though LED is instant
                await asyncio.sleep(2)
            
            # Let's use the global camera instance but handle the file movement.
            temp_filename = "timelapse_temp.jpg"
            # Use run_in_executor to prevent blocking the async loop during subprocess call
            captured_path = await asyncio.to_thread(
                camera.capture_image, 
                filename=temp_filename, 
                ev=-1.0, 
                saturation=0.8, 
                metering="average",
                width=1920,
                height=1080
            )
            
            if os.path.exists(captured_path) and not captured_path.startswith("Error"):
                target_path = os.path.join(IMAGES_DIR, filename)
                
                # Add timestamp overlay using ffmpeg
                try:
                    date_str = datetime.now().strftime("%m/%d/%Y %H:%M")
                    # Escape colons for ffmpeg filter syntax
                    # In drawtext filter, : is a delimiter, so we need to escape it as \:
                    # And since we are passing it in a string, we might need double escape depending on how it's parsed.
                    # Usually just replacing : with \: works for the text parameter.
                    # But also spaces sometimes need escaping if not quoted, but single quotes around text handles spaces.
                    
                    escaped_date_str = date_str.replace(":", r"\:")
                    
                    # We write to a temp file then move it over to be safe
                    temp_overlay_path = os.path.join(IMAGES_DIR, f"temp_{filename}")
                    
                    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
                    
                    # ffmpeg command to draw text in bottom left
                    # box=1:boxcolor=black@0.5 creates a semi-transparent background for readability
                    cmd = [
                        "ffmpeg", "-y",
                        "-i", captured_path,
                        "-vf", f"drawtext=fontfile={font_path}:text='{escaped_date_str}':fontcolor=white:fontsize=48:box=1:boxcolor=black@0.5:boxborderw=5:x=20:y=h-th-20",
                        temp_overlay_path
                    ]
                    
                    # Run ffmpeg (quietly)
                    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    
                    # If successful, use the overlaid image
                    if os.path.exists(temp_overlay_path):
                        os.rename(temp_overlay_path, target_path)
                        os.remove(captured_path) # Remove the original raw capture
                    else:
                        # Fallback to original if overlay failed
                        os.rename(captured_path, target_path)
                        logger.warning("Overlay file creation failed, using original image.")
                        
                except Exception as e:
                    logger.error(f"Failed to add timestamp overlay: {e}")
                    # Fallback to original
                    if os.path.exists(captured_path):
                        os.rename(captured_path, target_path)

                logger.info(f"Captured timelapse image: {target_path}")
                return target_path
            else:
                logger.error(f"Camera capture failed: {captured_path}")
                
        except Exception as e:
            logger.error(f"Failed to capture timelapse image: {e}")
            
        finally:
            # Ensure light is restored to original state even if error occurs
            if not was_active:
                ac_relay.turn_off()

def generate_timelapse_video():
    """
    Stitches all images in timeLapse/images into a video.
    """
    try:
        # Get list of images to verify we have any
        images = sorted(glob(os.path.join(IMAGES_DIR, "*.jpg")))
        if not images:
            logger.info("No images to stitch.")
            return

        date_str = datetime.now().strftime("%Y-%m-%d")
        video_filename = f"{date_str}.mp4"
        video_path = os.path.join(VIDEO_DIR, video_filename)

        # ffmpeg command using glob
        # We use -framerate 48 to achieve "1 day (48 images) = 1 second"
        cmd = [
            "ffmpeg",
            "-y",
            "-framerate", "9.6",
            "-pattern_type", "glob",
            "-i", os.path.join(IMAGES_DIR, "*.jpg"),
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-vf", "scale=1920:-2",
            "-crf", "28",
            "-preset", "slow",
            video_path
        ]
        
        logger.info(f"Starting video generation with {len(images)} images.")
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        logger.info(f"Video generated: {video_path}")
        
    except subprocess.CalledProcessError as e:
        logger.error(f"ffmpeg failed: {e}")
    except Exception as e:
        logger.error(f"Video generation failed: {e}")

async def timelapse_service():
    """
    Background service to run the timelapse logic.
    Captures image every 30 minutes.
    Updates video after capture.
    """
    logger.info("Timelapse service started.")
    while True:
        try:
            await capture_timelapse_image()
            # Offload video generation to thread or just run it (it might take a few seconds)
            # Since this is an async loop, blocking for ffmpeg is bad if it takes long.
            # But for a few images it's fine. For thousands, it might block the event loop.
            # We should run it in an executor.
            await asyncio.to_thread(generate_timelapse_video)
            
            # Sleep for 30 minutes
            await asyncio.sleep(1800)
            
        except asyncio.CancelledError:
            logger.info("Timelapse service stopped.")
            break
        except Exception as e:
            logger.error(f"Error in timelapse service: {e}")
            await asyncio.sleep(60) # Retry after a minute on error
