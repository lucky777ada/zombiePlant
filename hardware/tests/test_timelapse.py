
import os
import subprocess
from unittest.mock import patch, MagicMock
from src.logic.timelapse import generate_timelapse_video

@patch("src.logic.timelapse.glob")
@patch("src.logic.timelapse.subprocess.run")
def test_generate_timelapse_video_framerate(mock_run, mock_glob):
    # Setup mock images
    mock_glob.return_value = ["img1.jpg", "img2.jpg"]
    
    # Run the function
    generate_timelapse_video()
    
    # Verify subprocess.run was called
    assert mock_run.called
    
    # Get the arguments passed to subprocess.run
    args, kwargs = mock_run.call_args
    cmd = args[0]
    
    # Check for framerate 9.6
    assert "-framerate" in cmd
    idx = cmd.index("-framerate")
    assert cmd[idx + 1] == "9.6"
    
    # Check other critical flags
    assert "-pattern_type" in cmd
    assert "glob" in cmd

    # Check optimization flags
    assert "-crf" in cmd
    assert cmd[cmd.index("-crf") + 1] == "28"
    assert "-preset" in cmd
    assert cmd[cmd.index("-preset") + 1] == "slow"
    assert "-vf" in cmd
    assert "scale=1920:-2" in cmd[cmd.index("-vf") + 1]

@patch("src.logic.timelapse.glob")
@patch("src.logic.timelapse.subprocess.run")
def test_generate_timelapse_no_images(mock_run, mock_glob):
    # Setup empty images
    mock_glob.return_value = []
    
    generate_timelapse_video()
    
    # Should not run ffmpeg if no images
    assert not mock_run.called
