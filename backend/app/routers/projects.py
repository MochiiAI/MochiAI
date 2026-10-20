"""Project metadata CRUD (backend-side mirror of the repos Mochi works on)."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Device, Project
from app.schemas import ProjectCreate, ProjectResponse
from app.security import get_current_device

router = APIRouter(prefix="/api/projects", tags=["projects"])


@router.get("", response_model=list[ProjectResponse])
async def list_projects(
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
) -> list[Project]:
    return list(
        db.scalars(
            select(Project)
            .where(Project.device_id == current_device.id)
            .order_by(Project.updated_at.desc())
        )
    )


@router.post("", response_model=ProjectResponse, status_code=status.HTTP_201_CREATED)
async def create_project(
    payload: ProjectCreate,
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
) -> Project:
    existing = db.scalar(
        select(Project).where(
            Project.device_id == current_device.id,
            Project.repository_full_name == payload.repository_full_name.lower(),
        )
    )
    if existing is not None:
        raise HTTPException(status_code=409, detail="Project already registered for this device")

    project = Project(
        device_id=current_device.id,
        repository_full_name=payload.repository_full_name.lower(),
        checkout_path=payload.checkout_path,
        branch=payload.branch,
    )
    db.add(project)
    db.commit()
    db.refresh(project)
    return project


@router.delete("/{project_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_project(
    project_id: str,
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
) -> None:
    project = db.scalar(
        select(Project).where(Project.id == project_id, Project.device_id == current_device.id)
    )
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    db.delete(project)
    db.commit()