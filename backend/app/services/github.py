"""GitHub REST API client used to fetch issues and open pull requests.

The service is shared by the REST routes and the WebSocket task executor.
Tokens are resolved in this priority order:

1. token passed explicitly (from the Android app's stored PAT),
2. default "MochiiAI" bot token from settings.

The terminal / PRoot environment uses the very same token (injected into
``~/.netrc`` by ``preroot/setup_git_auth.sh``) so REST actions and
``git clone|commit|push`` always travel under one identity.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import httpx

from app.config import settings

GITHUB_API_URL = "https://api.github.com"


class GitHubServiceError(RuntimeError):
    """Raised for non-2xx GitHub API responses."""


@dataclass
class GitHubService:
    token: str | None = None
    _client: httpx.AsyncClient = field(default=None, repr=False)  # type: ignore[assignment]

    def __post_init__(self) -> None:
        self.token = self.token or settings.mochi_default_github_token

    async def _request(
        self,
        method: str,
        path: str,
        *,
        json: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
    ) -> Any:
        if not self.token:
            raise GitHubServiceError("No GitHub token configured (set MOCHI_DEFAULT_GITHUB_TOKEN).")

        if self._client is None:  # pragma: no cover - defensive
            self._client = httpx.AsyncClient(timeout=30.0)

        response = await self._client.request(
            method,
            f"{GITHUB_API_URL}{path}",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
            json=json,
            params=params,
        )
        if response.status_code >= 400:
            detail = response.text[:500]
            raise GitHubServiceError(f"GitHub API {response.status_code} on {path}: {detail}")
        if response.status_code == 204:
            return None
        return response.json()

    async def get_authenticated_user(self) -> dict[str, Any]:
        return await self._request("GET", "/user")

    async def list_issues(
        self, repo_full_name: str, *, state: str = "open", limit: int = 30
    ) -> list[dict[str, Any]]:
        issues = await self._request(
            "GET",
            f"/repos/{repo_full_name}/issues",
            params={"state": state, "per_page": min(limit, 100)},
        )
        return issues

    async def get_issue(self, repo_full_name: str, issue_number: int) -> dict[str, Any]:
        return await self._request("GET", f"/repos/{repo_full_name}/issues/{issue_number}")

    async def create_issue(
        self, repo_full_name: str, *, title: str, body: str, labels: list[str] | None = None
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"title": title, "body": body}
        if labels:
            payload["labels"] = labels
        return await self._request("POST", f"/repos/{repo_full_name}/issues", json=payload)

    async def create_pull_request(
        self,
        repo_full_name: str,
        *,
        title: str,
        head: str,
        base: str = "main",
        body: str | None = None,
        draft: bool = False,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"title": title, "head": head, "base": base, "draft": draft}
        if body:
            payload["body"] = body
        return await self._request("POST", f"/repos/{repo_full_name}/pulls", json=payload)

    async def get_repository(self, repo_full_name: str) -> dict[str, Any]:
        return await self._request("GET", f"/repos/{repo_full_name}")

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()

    async def __aenter__(self) -> GitHubService:
        self._client = httpx.AsyncClient(timeout=30.0)
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.close()