"""Pydantic request/response schemas."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models import AgentState, TaskStatus


# ---- Auth -----------------------------------------------------------------
class DeviceRegisterRequest(BaseModel):
    device_name: str = Field(min_length=1, max_length=128)
    # SHA-256 hex digest of the device public key (or of a per-install secret).
    public_key_digest: str = Field(min_length=32, max_length=128)


class TokenResponse(BaseModel):
    token: str
    token_type: str = "bearer"
    expires_at: datetime
    device_id: str


# ---- Projects -------------------------------------------------------------
class ProjectCreate(BaseModel):
    repository_full_name: str = Field(min_length=1, max_length=255, pattern=r"^[\w.-]+/[\w.-]+$")
    checkout_path: str | None = None
    branch: str | None = None


class ProjectResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    repository_full_name: str
    checkout_path: str | None
    branch: str | None
    created_at: datetime
    updated_at: datetime


# ---- Tasks -----------------------------------------------------------------
class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    prompt: str = Field(min_length=1)
    project_id: str | None = None


class TaskResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    prompt: str
    status: TaskStatus
    result: str | None
    error: str | None
    created_at: datetime
    started_at: datetime | None
    finished_at: datetime | None
    project_id: str | None


# ---- LLM proxy --------------------------------------------------------------
class LLMChatMessage(BaseModel):
    role: str = Field(pattern="^(system|user|assistant)$")
    content: str


class LLMChatRequest(BaseModel):
    messages: list[LLMChatMessage] = Field(min_length=1)
    temperature: float = Field(default=0.4, ge=0.0, le=2.0)
    max_tokens: int | None = Field(default=4096, ge=1, le=32768)
    stream: bool = False
    provider: str | None = Field(default=None, pattern="^(openai|anthropic)$")


class LLMChatResponse(BaseModel):
    provider: str
    model: str
    content: str
    usage: dict[str, int]


# ---- Agent state -----------------------------------------------------------
class AgentStateUpdate(BaseModel):
    state: AgentState
    action_name: str | None = None
    action_detail: str | None = None
    repo_full_name: str | None = None
    message: str | None = None


# ---- Health ----------------------------------------------------------------
class HealthResponse(BaseModel):
    status: str
    version: str = "0.1.0"
    database: str