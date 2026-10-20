"""WebSocket layer: connection hub + authenticated device endpoint."""

from app.ws.hub import ConnectionHub, hub

__all__ = ["ConnectionHub", "hub"]