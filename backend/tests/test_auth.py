"""Auth endpoint tests: registration, duplicate registration, refresh, rejection."""

from __future__ import annotations

from app.security import sha256_digest


def test_register_device_returns_bearer_token(client):
    resp = client.post(
        "/api/auth/register",
        json={"device_name": "pixel-9", "public_key_digest": "b" * 64},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["token_type"] == "bearer"
    assert len(body["token"]) > 20
    assert body["device_id"]


def test_register_same_device_returns_same_device_new_token(client):
    first = client.post(
        "/api/auth/register",
        json={"device_name": "dupe", "public_key_digest": "c" * 64},
    )
    second = client.post(
        "/api/auth/register",
        json={"device_name": "renamed", "public_key_digest": "c" * 64},
    )
    assert first.status_code == second.status_code == 201
    assert first.json()["device_id"] == second.json()["device_id"]
    assert first.json()["token"] != second.json()["token"]


def test_register_hashing(client):
    digest = sha256_digest("d" * 64)
    assert len(digest) == 64


def test_protected_route_rejects_missing_token(client):
    resp = client.get("/api/projects")
    assert resp.status_code == 401


def test_protected_route_rejects_bad_token(client):
    resp = client.get("/api/projects", headers={"Authorization": "Bearer garbage"})
    assert resp.status_code == 401


def test_refresh_requires_valid_token(client, auth_headers):
    resp = client.post("/api/auth/refresh", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["token"]