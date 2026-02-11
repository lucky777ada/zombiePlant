import asyncio
import time
import uuid
import logging
import traceback
from typing import Dict, Optional, Union, Any
from src.models import (
    JobType, JobState, JobRequest, JobStatus,
    FillResponse, EmptyResponse, FlushResponse, FeedResponse, DiagnosticResponse, NutrientRecipe
)
from src.state import system_lock
from src.logic.common import fill_to_max_logic, empty_tank_logic
from src.logic.flush import execute_system_flush
from src.logic.feed import execute_feed_cycle
from src.logic.diagnose import execute_diagnostic_check

# Setup logging
logger = logging.getLogger("jobs")

class JobManager:
    def __init__(self):
        self.jobs: Dict[str, JobStatus] = {}
        self.tasks: Dict[str, asyncio.Task] = {}

    async def _run_job(self, job_id: str, job_type: JobType, params: Dict[str, Any]):
        job = self.jobs[job_id]
        
        # Update status to running
        job.status = JobState.running
        job.started_at = time.time()
        
        try:
            # Acquire system lock for all hardware jobs to prevent conflicts
            async with system_lock:
                result = None
                
                if job_type == JobType.fill_to_max:
                    result = await fill_to_max_logic()
                
                elif job_type == JobType.empty_tank:
                    result = await empty_tank_logic()
                
                elif job_type == JobType.system_flush:
                    # Parse optional soak_duration from params, default to 180
                    soak = params.get("soak_duration", 180)
                    result = await execute_system_flush(soak_duration=int(soak))
                
                elif job_type == JobType.feed:
                    recipe = params.get("recipe")
                    if not recipe:
                        raise ValueError("Feed job requires 'recipe' parameter")
                    
                    # Convert string to Enum if needed
                    if isinstance(recipe, str):
                        try:
                            recipe = NutrientRecipe(recipe)
                        except ValueError:
                             raise ValueError(f"Invalid recipe: {recipe}")
                             
                    custom_amounts = params.get("amounts_ml")
                    result = await execute_feed_cycle(recipe=recipe, custom_amounts=custom_amounts)
                
                elif job_type == JobType.diagnose:
                    result = await execute_diagnostic_check()
                
                else:
                    raise ValueError(f"Unknown job type: {job_type}")
                
                # Update job on success
                job.status = JobState.completed
                job.completed_at = time.time()
                job.result = result

        except asyncio.CancelledError:
            job.status = JobState.failed
            job.error = "Job cancelled"
            job.completed_at = time.time()
            logger.info(f"Job {job_id} cancelled")
            raise

        except Exception as e:
            job.status = JobState.failed
            job.error = str(e)
            job.completed_at = time.time()
            logger.error(f"Job {job_id} failed: {e}")
            logger.error(traceback.format_exc())
            # We don't re-raise to avoid crashing the loop, the status captures the error

    def submit_job(self, request: JobRequest) -> str:
        job_id = str(uuid.uuid4())
        
        job_status = JobStatus(
            job_id=job_id,
            type=request.type,
            status=JobState.queued,
            created_at=time.time()
        )
        
        self.jobs[job_id] = job_status
        
        # Create background task
        # Ensure params is a dict and cast to Dict[str, Any] to satisfy type checker invariance
        job_params: Dict[str, Any] = request.params if request.params is not None else {}
        task = asyncio.create_task(self._run_job(job_id, request.type, job_params))
        self.tasks[job_id] = task
        
        return job_id

    def get_job(self, job_id: str) -> Optional[JobStatus]:
        return self.jobs.get(job_id)

    def cancel_job(self, job_id: str) -> bool:
        if job_id in self.tasks:
            task = self.tasks[job_id]
            if not task.done():
                task.cancel()
                return True
        return False

# Global instance
job_manager = JobManager()
