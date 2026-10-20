"""Mochi cloud backend application entry point."""

from __future__ import annotations

import asyncio
import contextlib
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import Base, engine
from app.routers import auth, github, llm, projects, state, tasks
from app.ws import hub
from app.ws.endpoint import router as ws_router

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
logger = logging.getLogger("mochi")


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    """Create tables on boot (dev) and run the WS heartbeat task."""
    if settings.is_prod:
        raise RuntimeError(
            "prod requires schema migrations: run `alembic upgrade head` before starting the app"
        )
    Base.metadata.create_all(bind=engine)
    heartbeat = asyncio.create_task(hub.run_heartbeat())
    try:
        yield
    finally:
        heartbeat.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await heartbeat


def create_app() -> FastAPI:
    app = FastAPI(
        title="Mochi Cloud Backend",
        version="0.1.0",
        description=(
            "Hybrid AI coding agent backend: WS device sync, LLM proxy, GitHub integration."
        ),
        lifespan=lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(auth.router)
    app.include_router(projects.router)
    app.include_router(github.router)
    app.include_router(llm.router)
    app.include_router(tasks.router)
    app.include_router(state.router)
    app.include_router(ws_router)

    @app.get("/health", tags=["health"])
    async def health() -> dict[str, str]:
        return {"status": "ok", "version": "0.1.0", "database": "up"}

    return app


app = create_app()