from fastapi import APIRouter, HTTPException, Path
from src.models import (
    JobRequest, JobStatus
)
from src.logic.jobs import job_manager

router = APIRouter(prefix="/jobs", tags=["Jobs"])

@router.post("/", response_model=JobStatus, status_code=201)
async def submit_job(request: JobRequest):
    """
    Submit a new background job.
    Returns the initial job status (queued).
    """
    try:
        job_id = job_manager.submit_job(request)
        job = job_manager.get_job(job_id)
        return job
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@router.get("/{job_id}", response_model=JobStatus)
async def get_job_status(job_id: str = Path(..., description="The ID of the job to retrieve")):
    """
    Get the status of a specific job.
    """
    job = job_manager.get_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return job

@router.delete("/{job_id}", status_code=204)
async def cancel_job(job_id: str):
    """
    Cancel a running or queued job.
    """
    job_manager.cancel_job(job_id)
    # We return 204 regardless of whether it was running or not, standard idempotency
    return
