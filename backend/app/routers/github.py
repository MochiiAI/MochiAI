"""GitHub REST proxy endpoints.

The Android app sends its stored PAT in ``X-Mochi-GitHub-Token``; if absent the
server falls back to the default ``MochiiAI`` account token. These endpoints keep
the app from embedding its own GitHub SDK and centralise rate limits.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Query, status

from app.models import Device
from app.security import get_current_device
from app.services.github import GitHubService, GitHubServiceError

router = APIRouter(prefix="/api/github", tags=["github"])


def _service(x_mochi_github_token: str | None) -> GitHubService:
    return GitHubService(token=x_mochi_github_token)


def _handle(exc: GitHubServiceError) -> None:
    raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)) from exc


@router.get("/user")
async def github_user(
    x_mochi_github_token: str | None = Header(default=None),
    _device: Device = Depends(get_current_device),
) -> dict[str, Any]:
    try:
        return await _service(x_mochi_github_token).get_authenticated_user()
    except GitHubServiceError as exc:
        _handle(exc)


@router.get("/repos/{repo_full_name:path}/issues")
async def list_issues(
    repo_full_name: str,
    state: str = Query(default="open", pattern="^(open|closed|all)$"),
    limit: int = Query(default=30, ge=1, le=100),
    x_mochi_github_token: str | None = Header(default=None),
    _device: Device = Depends(get_current_device),
) -> list[dict[str, Any]]:
    try:
        svc = _service(x_mochi_github_token)
        return await svc.list_issues(repo_full_name, state=state, limit=limit)
    except GitHubServiceError as exc:
        _handle(exc)


@router.post("/repos/{repo_full_name:path}/issues", status_code=status.HTTP_201_CREATED)
async def create_issue(
    repo_full_name: str,
    payload: dict[str, Any],
    x_mochi_github_token: str | None = Header(default=None),
    _device: Device = Depends(get_current_device),
) -> dict[str, Any]:
    try:
        return await _service(x_mochi_github_token).create_issue(
            repo_full_name,
            title=str(payload["title"]),
            body=str(payload.get("body", "")),
            labels=payload.get("labels"),
        )
    except GitHubServiceError as exc:
        _handle(exc)


@router.post("/repos/{repo_full_name:path}/pulls", status_code=status.HTTP_201_CREATED)
async def create_pull_request(
    repo_full_name: str,
    payload: dict[str, Any],
    x_mochi_github_token: str | None = Header(default=None),
    _device: Device = Depends(get_current_device),
) -> dict[str, Any]:
    try:
        return await _service(x_mochi_github_token).create_pull_request(
            repo_full_name,
            title=str(payload["title"]),
            head=str(payload["head"]),
            base=str(payload.get("base", "main")),
            body=payload.get("body"),
            draft=bool(payload.get("draft", False)),
        )
    except GitHubServiceError as exc:
        _handle(exc)


@router.get("/repos/{repo_full_name:path}")
async def get_repository(
    repo_full_name: str,
    x_mochi_github_token: str | None = Header(default=None),
    _device: Device = Depends(get_current_device),
) -> dict[str, Any]:
    try:
        return await _service(x_mochi_github_token).get_repository(repo_full_name)
    except GitHubServiceError as exc:
        _handle(exc)