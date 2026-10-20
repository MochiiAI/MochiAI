"""Connection hub for device-facing WebSockets.

Responsibilities:
* keep one WebSocket per authenticated device,
* multiplex push events from background tasks to the right device,
* forward client-initiated state changes to the companion device(s), and
* periodically broadcast agent-state heartbeats so the app can reconcile.
"""

from __future__ import annotations

import asyncio
import json
import logging
from datetime import UTC, datetime
from typing import Any, Final

from starlette.websockets import WebSocket, WebSocketDisconnect

from app.models import AgentState

logger = logging.getLogger("mochi.ws.hub")

HEARTBEAT_INTERVAL: Final[float] = 30.0


def _now() -> str:
    return datetime.now(UTC).isoformat()


class ConnectionHub:
    """A simple per-device fan-out hub."""

    def __init__(self) -> None:
        self._connections: dict[str, set[WebSocket]] = {}
        self._state: dict[str, AgentState] = {}
        self._lock = asyncio.Lock()

    # -- lifecycle -----------------------------------------------------------
    async def connect(self, device_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self._lock:
            self._connections.setdefault(device_id, set()).add(websocket)
            current = self._state.get(device_id, AgentState.IDLE)
        logger.info("device connected", extra={"device_id": device_id})
        await self._send(
            websocket,
            {
                "type": "hello",
                "state": current.value,
                "heartbeat_interval": HEARTBEAT_INTERVAL,
            },
        )

    async def disconnect(self, device_id: str, websocket: WebSocket) -> None:
        async with self._lock:
            sockets = self._connections.get(device_id)
            if not sockets:
                return
            sockets.discard(websocket)
            if not sockets:
                self._connections.pop(device_id, None)
        logger.info("device disconnected", extra={"device_id": device_id})

    async def _send(self, websocket: WebSocket, payload: dict[str, Any]) -> None:
        await websocket.send_text(json.dumps(payload))

    # -- publish helpers ------------------------------------------------------
    async def publish_agent_state(
        self,
        device_id: str,
        state: AgentState,
        *,
        action_name: str | None = None,
        action_detail: str | None = None,
        message: str | None = None,
    ) -> None:
        async with self._lock:
            self._state[device_id] = state
        payload: dict[str, Any] = {
            "type": "agent.state",
            "state": state.value,
            "action_name": action_name,
            "action_detail": action_detail,
            "message": message,
            "sent_at": _now(),
        }
        await self.broadcast(device_id, payload)

    async def publish_event(self, device_id: str, event: str, data: dict[str, Any]) -> None:
        await self.broadcast(
            device_id,
            {"type": "event", "event": event, "data": data, "sent_at": _now()},
        )

    async def broadcast(self, device_id: str, payload: dict[str, Any]) -> None:
        """Send ``payload`` to every socket registered for ``device_id``."""
        async with self._lock:
            sockets = list(self._connections.get(device_id, ()))
        dead: list[WebSocket] = []
        for socket in sockets:
            try:
                await self._send(socket, payload)
            except (RuntimeError, WebSocketDisconnect):
                dead.append(socket)
        for socket in dead:
            await self.disconnect(device_id, socket)

    async def handle_client_message(self, device_id: str, websocket: WebSocket, text: str) -> None:
        """Validate and act on a message from the Android app."""
        try:
            msg = json.loads(text)
        except json.JSONDecodeError:
            await self._send(websocket, {"type": "error", "detail": "invalid JSON"})
            return
        if not isinstance(msg, dict) or "type" not in msg:
            await self._send(websocket, {"type": "error", "detail": "missing 'type' field"})
            return

        msg_type = msg["type"]
        if msg_type == "agent.state":
            try:
                state = AgentState(msg["state"])
            except ValueError:
                await self._send(
                    websocket,
                    {"type": "error", "detail": f"unknown state {msg.get('state')}"},
                )
                return
            async with self._lock:
                self._state[device_id] = state
            await self.broadcast(
                device_id,
                {
                    "type": "agent.state",
                    "state": state.value,
                    "action_name": msg.get("action_name"),
                    "action_detail": msg.get("action_detail"),
                    "message": msg.get("message"),
                    "sent_at": _now(),
                },
            )
        elif msg_type == "ping":
            await self._send(websocket, {"type": "pong", "at": _now()})
        elif msg_type == "state.get":
            async with self._lock:
                current = self._state.get(device_id, AgentState.IDLE)
            await self._send(websocket, {"type": "state", "state": current.value})
        else:
            await self._send(
                websocket,
                {"type": "error", "detail": f"unhandled message type {msg_type}"},
            )

    async def run_heartbeat(self) -> None:
        while True:
            await asyncio.sleep(HEARTBEAT_INTERVAL)
            async with self._lock:
                snapshot = {k: v.value for k, v in self._state.items()}
            for device_id, state_value in snapshot.items():
                await self.broadcast(
                    device_id,
                    {"type": "heartbeat", "state": state_value, "sent_at": _now()},
                )

    @property
    def connected_devices(self) -> list[str]:
        return list(self._connections.keys())

    @property
    def current_states(self) -> dict[str, str]:
        return {k: v.value for k, v in self._state.items()}


hub = ConnectionHub()