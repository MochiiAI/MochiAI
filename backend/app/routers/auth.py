"""Device registration and token refresh endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Depends, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Device
from app.schemas import DeviceRegisterRequest, TokenResponse
from app.security import get_current_device, issue_device_session, sha256_digest

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
async def register_device(
    payload: DeviceRegisterRequest, db: Session = Depends(get_db)
) -> TokenResponse:
    """Register a new device (or re-issue a token for an existing one).

    The ``public_key_digest`` is a SHA-256 digest of the device's public key or
    per-install secret. Registering twice with the same digest returns a fresh
    session for the same device.
    """
    digest = sha256_digest(payload.public_key_digest.strip())
    device = db.scalar(select(Device).where(Device.public_key_digest == digest))
    if device is None:
        device = Device(device_name=payload.device_name, public_key_digest=digest)
        db.add(device)
        db.commit()
        db.refresh(device)

    token, expires_at = issue_device_session(db, device)
    return TokenResponse(token=token, expires_at=expires_at, device_id=device.id)


@router.post("/refresh", response_model=TokenResponse)
async def refresh_token(
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
) -> TokenResponse:
    """Issue a fresh session for an already-authenticated device."""
    token, expires_at = issue_device_session(db, current_device)
    return TokenResponse(token=token, expires_at=expires_at, device_id=current_device.id)