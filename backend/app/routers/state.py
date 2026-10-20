"""Agent-state sync endpoints (REST fallback for the WebSocket channel)."""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import update
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import AgentState, Device
from app.models import Session as SessionModel
from app.schemas import AgentStateUpdate
from app.security import get_current_device
from app.ws.hub import hub

router = APIRouter(prefix="/api/state", tags=["state"])


@router.get("", response_model=dict[str, str])
async def get_device_state(
    current_device: Device = Depends(get_current_device),
) -> dict[str, str]:
    """Return the last known agent state for the requesting device."""
    state = hub.current_states.get(current_device.id, AgentState.IDLE.value)
    return {"state": state}


@router.put("", response_model=dict[str, str])
async def update_device_state(
    payload: AgentStateUpdate,
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    """Persist + broadcast a state transition (used by the app when the local
    agent changes state; cloud executor pushes also flow through the hub)."""
    db.execute(
        update(SessionModel)
        .where(SessionModel.device_id == current_device.id)
        .values(last_agent_state=payload.state.value)
    )
    db.commit()
    await hub.publish_agent_state(
        current_device.id,
        payload.state,
        action_name=payload.action_name,
        action_detail=payload.action_detail,
        message=payload.message,
    )
    return {"state": payload.state.value}