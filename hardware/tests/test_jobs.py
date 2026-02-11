import pytest
import asyncio
from unittest.mock import patch, AsyncMock
from src.models import JobType, JobState

class TestJobs:
    @pytest.mark.asyncio
    async def test_empty_tank_job_already_empty(self, async_client, mock_hardware):
        """
        Test that we can submit a job, it runs in background, and we can get its result.
        Using empty_tank when already empty ensures immediate completion.
        """
        client = async_client
        # Set hardware state to Empty
        mock_hardware.set_water_level(full=False, empty=True)
        
        # 1. Submit Job
        payload = {"type": "empty_tank", "params": {}}
        response = await client.post("/jobs/", json=payload)
        assert response.status_code == 201
        data = response.json()
        job_id = data["job_id"]
        assert data["status"] == "queued"
        
        # 2. Yield control to event loop to let the background task start/finish
        # The background task is created with asyncio.create_task.
        # We need to await something to let it run.
        await asyncio.sleep(0.1)
        
        # 3. Poll Status
        response = await client.get(f"/jobs/{job_id}")
        assert response.status_code == 200
        data = response.json()
        
        assert data["status"] == "completed"
        assert data["result"]["status"] == "success"
        assert data["result"]["message"] == "Tank already empty"

    @pytest.mark.asyncio
    async def test_cancel_job(self, async_client, mock_hardware):
        """
        Test cancelling a long running job.
        We'll use fill_to_max with a mock that never fills, so it loops.
        """
        client = async_client
        mock_hardware.set_water_level(full=False, empty=False)
        
        # Submit fill job
        payload = {"type": "fill_to_max"}
        response = await client.post("/jobs/", json=payload)
        job_id = response.json()["job_id"]
        
        # Yield to let it start
        await asyncio.sleep(0.1)
        
        # Check it's running
        response = await client.get(f"/jobs/{job_id}")
        data = response.json()
        assert data["status"] == "running", f"Job failed with error: {data.get('error')}"
        
        # Cancel it
        response = await client.delete(f"/jobs/{job_id}")
        assert response.status_code == 204
        
        # Yield to let cancellation exception propagate
        await asyncio.sleep(0.1)
        
        # Check status is failed (cancelled)
        response = await client.get(f"/jobs/{job_id}")
        data = response.json()
        assert data["status"] == "failed"
        assert "cancelled" in data["error"].lower()
