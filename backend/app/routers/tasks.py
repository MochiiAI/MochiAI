"""Cloud task delegation: create a task and stream state over WebSockets."""

from __future__ import annotations

import asyncio

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import SessionLocal, get_db
from app.models import Device, Project, Task, TaskStatus
from app.schemas import TaskCreate, TaskResponse
from app.security import get_current_device
from app.services.executor import run_task
from app.services.github import GitHubService
from app.services.llm import LLMError, get_provider
from app.ws.hub import hub

router = APIRouter(prefix="/api/tasks", tags=["tasks"])


@router.post("", response_model=TaskResponse, status_code=status.HTTP_202_ACCEPTED)
async def create_task(
    payload: TaskCreate,
    background_tasks: BackgroundTasks,
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
) -> Task:
    """Queue a cloud task.

    The task runs in FastAPI's background task pool and emits
    ``agent.state`` / ``event`` frames on the device's WebSocket channel, so
    the Android app can animate the Mochi avatar accordingly.
    """
    project: Project | None = None
    if payload.project_id:
        project = db.scalar(
            select(Project).where(
                Project.id == payload.project_id, Project.device_id == current_device.id
            )
        )
        if project is None:
            raise HTTPException(status_code=404, detail="Project not found")

    task = Task(
        device_id=current_device.id,
        title=payload.title,
        prompt=payload.prompt,
        project_id=project.id if project else None,
        status=TaskStatus.PENDING,
    )
    db.add(task)
    db.commit()
    db.refresh(task)

    def _run() -> None:
        # Background tasks must not reuse the request-scoped session.
        db_copy = SessionLocal()
        try:
            try:
                provider = get_provider(None)
            except LLMError as exc:
                task.status = TaskStatus.FAILED
                task.error = str(exc)
                db_copy.merge(task)
                db_copy.commit()
                return
            github = GitHubService()
            asyncio.run(
                run_task(
                    task=task,
                    db=db_copy,
                    hub=hub,
                    provider=provider,
                    github=github,
                )
            )
        finally:
            db_copy.close()

    background_tasks.add_task(_run)
    return task