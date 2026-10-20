"""WebSocket endpoint: authenticated, per-device sync channel.

Wire protocol is documented in ``docs/WS_PROTOCOL.md``. Authentication uses a
JWT passed as ``?token=`` in the connection query string (the standard for
browser/WebSocket clients, which cannot send Authorization headers reliably).
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, WebSocket, WebSocketDisconnect
from jose import JWTError, jwt
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import Device
from app.models import Session as SessionModel
from app.security import sha256_digest
from app.ws.hub import hub

router = APIRouter(tags=["websocket"])


def _authenticate(token: str | None, db: Session) -> Device | None:
    if not token:
        return None
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    except JWTError:
        return None
    device_id = payload.get("sub")
    if not device_id:
        return None
    device = db.get(Device, device_id)
    if device is None:
        return None
    session = db.scalar(
        select(SessionModel).where(
            SessionModel.token_hash == sha256_digest(token),
            SessionModel.device_id == device_id,
        )
    )
    if session is None or session.status.value != "ACTIVE":
        return None
    return device


@router.websocket("/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str | None = Query(default=None),
    db: Session = Depends(get_db),
) -> None:
    device = _authenticate(token, db)
    if device is None:
        await websocket.close(code=4401, reason="unauthorized")
        return

    await hub.connect(device.id, websocket)
    try:
        while True:
            message = await websocket.receive_text()
            await hub.handle_client_message(device.id, websocket, message)
    except WebSocketDisconnect:
        pass
    finally:
        await hub.disconnect(device.id, websocket)