"""WebSocket sync tests: hello, ping/pong, state relay and auth rejection."""

from __future__ import annotations

import pytest
from starlette.testclient import WebSocketDisconnect


def _register(client, name, digest):
    resp = client.post(
        "/api/auth/register",
        json={"device_name": name, "public_key_digest": digest * 64},
    )
    assert resp.status_code == 201
    return resp.json()["token"]


def test_ws_requires_valid_token(client):
    with pytest.raises(WebSocketDisconnect), client.websocket_connect("/ws") as ws:
        ws.receive_text()


def test_ws_rejects_bad_token(client):
    with pytest.raises(WebSocketDisconnect), client.websocket_connect(
        "/ws?token=not-a-real-token"
    ) as ws:
        ws.receive_text()


def test_ws_hello_and_ping_pong(client):
    token = _register(client, "ws-device", "f")
    with client.websocket_connect(f"/ws?token={token}") as ws:
        hello = ws.receive_json()
        assert hello["type"] == "hello"
        assert hello["state"] == "IDLE"

        ws.send_json({"type": "ping"})
        pong = ws.receive_json()
        assert pong["type"] == "pong"


def test_ws_state_roundtrip(client):
    token = _register(client, "ws-device", "g")
    with client.websocket_connect(f"/ws?token={token}") as ws:
        ws.receive_json()  # hello
        ws.send_json({"type": "agent.state", "state": "CODING", "action_name": "writing tests"})
        echoed = ws.receive_json()
        assert echoed["type"] == "agent.state"
        assert echoed["state"] == "CODING"
        assert echoed["action_name"] == "writing tests"

        ws.send_json({"type": "state.get"})
        snapshot = ws.receive_json()
        assert snapshot["type"] == "state"
        assert snapshot["state"] == "CODING"


def test_ws_multiple_clients_same_device_both_receive(client):
    token = _register(client, "ws-device", "h")
    with client.websocket_connect(f"/ws?token={token}") as ws1, client.websocket_connect(
        f"/ws?token={token}"
    ) as ws2:
        ws1.receive_json()  # hello
        ws2.receive_json()  # hello

        ws1.send_json({"type": "agent.state", "state": "ACTION", "action_name": "running tests"})
        got1 = ws1.receive_json()
        got2 = ws2.receive_json()
        assert got1["state"] == got2["state"] == "ACTION"