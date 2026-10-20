"""Project and task REST tests (happy path + ownership isolation)."""

from __future__ import annotations


def test_project_lifecycle(client, auth_headers):
    # create
    resp = client.post(
        "/api/projects",
        json={"repository_full_name": "MochiiAI/Mochi", "branch": "main"},
        headers=auth_headers,
    )
    assert resp.status_code == 201, resp.text
    project = resp.json()
    assert project["repository_full_name"] == "mochiiai/mochi"

    # duplicate create -> 409
    dup = client.post(
        "/api/projects",
        json={"repository_full_name": "MochiiAI/Mochi"},
        headers=auth_headers,
    )
    assert dup.status_code == 409

    # list
    listing = client.get("/api/projects", headers=auth_headers)
    assert listing.status_code == 200
    assert any(p["id"] == project["id"] for p in listing.json())

    # delete
    deleted = client.delete(f"/api/projects/{project['id']}", headers=auth_headers)
    assert deleted.status_code == 204
    listing = client.get("/api/projects", headers=auth_headers)
    assert listing.json() == []


def test_project_ownership_scoped_to_device(client, auth_headers):
    client.post(
        "/api/projects",
        json={"repository_full_name": "Owner/Proj"},
        headers=auth_headers,
    )
    other = client.post(
        "/api/auth/register",
        json={"device_name": "other", "public_key_digest": "e" * 64},
    ).json()["token"]
    other_headers = {"Authorization": f"Bearer {other}"}
    listing = client.get("/api/projects", headers=other_headers)
    assert listing.json() == []


def test_create_task_returns_accepted(client, auth_headers):
    resp = client.post(
        "/api/tasks",
        json={"title": "explain", "prompt": "Explain pytest to me."},
        headers=auth_headers,
    )
    assert resp.status_code == 202, resp.text
    task = resp.json()
    assert task["status"] in {"PENDING", "RUNNING"}


def test_task_requires_project_ownership(client, auth_headers):
    resp = client.post(
        "/api/tasks",
        json={"title": "x", "prompt": "y", "project_id": "nonexistent"},
        headers=auth_headers,
    )
    assert resp.status_code == 404