from enum import Enum
from pydantic import BaseModel, Field
from typing import Dict, Literal, Union, Optional, Any

# --- Enums ---

class PumpID(str, Enum):
    water_out = "water_out"
    water_in = "water_in"
    flora_micro = "flora_micro"
    flora_gro = "flora_gro"
    flora_bloom = "flora_bloom"

class RelayState(str, Enum):
    on = "on"
    off = "off"

class NutrientRecipe(str, Enum):
    vegetative = "vegetative"
    flowering = "flowering"
    custom = "custom"

# --- Request Models ---

class PumpCommand(BaseModel):
    pump_id: PumpID = Field(..., description="The unique identifier for the pump.")
    duration: float = Field(..., gt=0, description="Duration in seconds for which to run the pump.", json_schema_extra={"example": 5.5})

class FeedRequest(BaseModel):
    recipe: NutrientRecipe = Field(..., description="Nutrient recipe to apply.")
    amounts_ml: Optional[Dict[str, float]] = Field(None, description="Custom nutrient amounts in mL (required if recipe is custom). Keys: micro, gro, bloom.")

class DoseRequest(BaseModel):
    nutrient: PumpID = Field(..., description="The nutrient pump to activate (flora_micro, flora_gro, flora_bloom).")
    amount_ml: float = Field(..., gt=0, description="Amount of nutrient to dispense in mL.")
    mix_seconds: int = Field(30, ge=0, description="Duration to run air stones for mixing after dosing.")

# --- Response Models ---

class StatusResponse(BaseModel):
    status: str = Field(..., json_schema_extra={"example": "Online"})
    system: str = Field(..., json_schema_extra={"example": "ZombiePlant V0.0.1"})

class SuccessResponse(BaseModel):
    status: Literal["success"] = "success"

class PumpResponse(SuccessResponse):
    pump: PumpID
    duration: float

class ACRelayResponse(SuccessResponse):
    ac_power: RelayState

class WaterLevelStatus(BaseModel):
    full: bool = Field(..., description="True if the top float switch is triggered.")
    empty: bool = Field(..., description="True if the bottom float switch is triggered.")

class TDSStatus(BaseModel):
    ppm: float = Field(..., description="Total Dissolved Solids in parts per million.")
    voltage: float = Field(..., description="Raw voltage reading from the sensor.")

class PHStatus(BaseModel):
    ph: float = Field(..., description="Calculated pH level (0-14).")
    voltage: float = Field(..., description="Raw voltage reading from the sensor.")

class DHTSuccess(BaseModel):
    temperature_f: float = Field(..., description="Temperature in Fahrenheit")
    humidity_percent: float = Field(..., description="Relative Humidity in percent")

class DHTError(BaseModel):
    error: str

class HardwareStatusResponse(BaseModel):
    pumps: Dict[PumpID, int] = Field(..., description="Current state of each pump (0=OFF, 1=ON).")
    ac_power: RelayState = Field(..., description="Current state of the AC power relay.")
    water_level: WaterLevelStatus
    tds: TDSStatus
    ph: PHStatus
    environment: Union[DHTSuccess, DHTError] = Field(..., description="Air temperature and humidity.")

class FillResponse(SuccessResponse):
    message: str
    fill_duration: float
    adjust_duration: float

class EmptyResponse(SuccessResponse):
    message: str
    duration: float

class FlushResponse(SuccessResponse):
    message: str
    final_fill_details: FillResponse

class FeedResponse(SuccessResponse):
    message: str
    recipe: NutrientRecipe
    amounts_dispensed: Dict[str, float]
    final_tds: float

class DoseResponse(SuccessResponse):
    message: str
    nutrient: PumpID
    amount_dispensed_ml: float
    final_tds: float

class ErrorResponse(BaseModel):
    detail: str

class CameraErrorResponse(BaseModel):
    error: Literal["Capture failed"]


# --- Diagnostic Models ---

class SensorCheckResult(BaseModel):
    passed: bool
    value: Union[float, Dict[str, Any], str]
    message: str

class PumpCheckResult(BaseModel):
    passed: bool
    pump_id: Optional[str] = None
    noise_level: float
    message: str

class DiagnosticResponse(BaseModel):
    status: Literal["healthy", "warning", "error"]
    sensors: Dict[str, SensorCheckResult]
    pumps: PumpCheckResult

# --- Job Models ---

class JobType(str, Enum):
    fill_to_max = "fill_to_max"
    empty_tank = "empty_tank"
    system_flush = "system_flush"
    feed = "feed"
    diagnose = "diagnose"

class JobState(str, Enum):
    queued = "queued"
    running = "running"
    completed = "completed"
    failed = "failed"

class JobRequest(BaseModel):
    type: JobType
    params: Optional[Dict[str, Union[str, float, int, dict]]] = Field(default_factory=dict, description="Parameters for the job (e.g., recipe for feed).")

class JobStatus(BaseModel):
    job_id: str
    type: JobType
    status: JobState
    created_at: float
    started_at: Optional[float] = None
    completed_at: Optional[float] = None
    result: Optional[Union[Dict, str, FillResponse, EmptyResponse, FlushResponse, FeedResponse, DiagnosticResponse]] = None
    error: Optional[str] = None
