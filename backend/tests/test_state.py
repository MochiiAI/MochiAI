"""Agent-state REST endpoints and state → hub broadcast."""

from __future__ import annotations


def test_state_get_and_put(client, auth_headers):
    get = client.get("/api/state", headers=auth_headers)
    assert get.status_code == 200
    assert get.json()["state"] == "IDLE"

    put = client.put(
        "/api/state",
        json={"state": "THINKING", "message": "thinking hard"},
        headers=auth_headers,
    )
    assert put.status_code == 200
    assert put.json()["state"] == "THINKING"

    get2 = client.get("/api/state", headers=auth_headers)
    assert get2.json()["state"] == "THINKING"


def test_state_put_validates_unknown_state(client, auth_headers):
    resp = client.put(
        "/api/state",
        json={"state": "SLEEPING"},
        headers=auth_headers,
    )
    assert resp.status_code == 422