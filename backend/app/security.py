"""Device authentication: JWT issuance/validation and FastAPI dependencies."""

from __future__ import annotations

import hashlib
import hmac
import secrets
from datetime import UTC, datetime, timedelta

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import Device
from app.models import Session as SessionModel

bearer_scheme = HTTPBearer(auto_error=False)

_CREDENTIALS_ERROR = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="Invalid authentication credentials",
    headers={"WWW-Authenticate": "Bearer"},
)


def sha256_digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def generate_session_token() -> str:
    """Return a raw bearer token plus its stored (hashed) form."""
    return secrets.token_urlsafe(48)


def create_access_token(device_id: str) -> tuple[str, datetime]:
    """Create a signed JWT for a device and its expiry timestamp.

    A random ``jti`` makes every issued token unique, even when two tokens are
    minted for the same device within the same clock tick (their hashes are
    stored in ``sessions.token_hash`` which is UNIQUE).
    """
    now = datetime.now(UTC)
    expires_at = now + timedelta(days=settings.jwt_expire_days)
    payload = {"sub": device_id, "iat": now, "exp": expires_at, "jti": secrets.token_urlsafe(16)}
    token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)
    return token, expires_at


def issue_device_session(db: Session, device: Device) -> tuple[str, datetime]:
    """Persist a hashed session token and return the raw JWT + expiry."""
    raw = create_access_token(device.id)
    token, expires_at = raw
    session = SessionModel(
        token_hash=sha256_digest(token),
        device_id=device.id,
        expires_at=expires_at,
    )
    db.add(session)
    db.commit()
    return token, expires_at


def get_current_device(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> Device:
    """Resolve the authenticated device from the bearer JWT."""
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise _CREDENTIALS_ERROR
    token = credentials.credentials
    try:
        payload = jwt.decode(
            token, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
        )
    except JWTError as exc:
        raise _CREDENTIALS_ERROR from exc

    device_id = payload.get("sub")
    if not device_id:
        raise _CREDENTIALS_ERROR

    device = db.get(Device, device_id)
    if device is None:
        raise _CREDENTIALS_ERROR

    # Reject if the corresponding persisted session was revoked.
    session = db.scalar(
        select(SessionModel).where(
            SessionModel.token_hash == sha256_digest(token),
            SessionModel.device_id == device_id,
        )
    )
    if session is None or session.status.value != "ACTIVE":
        raise _CREDENTIALS_ERROR
    return device


def constant_time_digest_eq(a: str, b: str) -> bool:
    return hmac.compare_digest(a, b)