"""Shared pytest fixtures.

Uses a single shared in-memory SQLite database (StaticPool) so that the
FastAPI app, its lifespan table creation, the dependency-provided sessions and
manual service sessions all hit the exact same tables.
"""

from __future__ import annotations

import os

os.environ["MOCHI_ENV"] = "dev"
os.environ["MOCHI_JWT_SECRET"] = "test-secret-do-not-use-in-prod"
os.environ["MOCHI_DATABASE_URL"] = "sqlite:///:memory:"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app import database as database_module  # noqa: E402
from app.database import Base  # noqa: E402

_engine = None
_TestingSession = None


@pytest.fixture(scope="session")
def shared_engine():
    """One in-memory database shared across the whole test session."""
    global _engine
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    _engine = engine
    return engine


@pytest.fixture()
def client(shared_engine, monkeypatch):
    """FastAPI TestClient wired to the shared in-memory database."""
    global _TestingSession
    _TestingSession = sessionmaker(
        bind=shared_engine, autoflush=False, autocommit=False, expire_on_commit=False
    )

    # Rebind the module singletons so lifespan, get_db and services agree.
    monkeypatch.setattr(database_module, "engine", shared_engine)
    monkeypatch.setattr(database_module, "SessionLocal", _TestingSession)

    Base.metadata.drop_all(bind=shared_engine)
    Base.metadata.create_all(bind=shared_engine)

    from app.main import create_app

    app = create_app()

    def override_get_db():
        db = _TestingSession()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[database_module.get_db] = override_get_db

    with TestClient(app) as c:
        yield c


@pytest.fixture(autouse=True)
def _clean_tables(shared_engine):
    """Reset all tables before each test regardless of fixture usage."""
    Base.metadata.create_all(bind=shared_engine)
    yield
    with shared_engine.begin() as conn:
        for table in reversed(Base.metadata.sorted_tables):
            conn.execute(table.delete())


def get_test_session():
    """Return a fresh session bound to the shared in-memory engine."""
    return _TestingSession()


@pytest.fixture()
def db_session(shared_engine, monkeypatch):
    """A standalone session for service-level tests."""
    TestingSession = sessionmaker(
        bind=shared_engine, autoflush=False, autocommit=False, expire_on_commit=False
    )
    db = TestingSession()
    try:
        yield db
    finally:
        db.close()


@pytest.fixture()
def auth_headers(client):
    """Register a device and return usable Authorization headers."""
    resp = client.post(
        "/api/auth/register",
        json={"device_name": "test-device", "public_key_digest": "a" * 64},
    )
    assert resp.status_code == 201, resp.text
    token = resp.json()["token"]
    return {"Authorization": f"Bearer {token}", "X-Mochi-GitHub-Token": "ghp_TESTTOKEN"}