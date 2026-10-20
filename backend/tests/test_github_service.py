"""GitHub REST service tests using a local mocked transport."""

from __future__ import annotations

from typing import Any

import pytest

from app.services.github import GitHubService, GitHubServiceError


class FakeTransport:
    """Minimal httpx-compatible async transport that records requests."""

    def __init__(self, routes: dict[tuple[str, str], Any]):
        self.routes = routes
        self.calls: list[dict[str, Any]] = []

    async def handle_async_request(self, request) -> Any:
        import httpx

        key = (request.method.upper(), str(request.url))
        self.calls.append({"method": request.method, "url": str(request.url)})
        if key in self.routes:
            body = self.routes[key]
            return httpx.Response(200, json=body, request=request)
        return httpx.Response(404, text="not found", request=request)


@pytest.fixture()
def client():
    import httpx

    transport = FakeTransport(
        {
            ("GET", "https://api.github.com/user"): {"login": "MochiiAI"},
            ("GET", "https://api.github.com/repos/MochiiAI/Mochi"): {
                "full_name": "MochiiAI/Mochi",
                "default_branch": "main",
                "language": "Python",
            },
            ("POST", "https://api.github.com/repos/MochiiAI/Mochi/pulls"): {
                "number": 42,
                "html_url": "https://github.com/MochiiAI/Mochi/pull/42",
            },
            ("POST", "https://api.github.com/repos/MochiiAI/Mochi/issues"): {
                "number": 7,
                "html_url": "https://github.com/MochiiAI/Mochi/issues/7",
            },
        }
    )
    svc = GitHubService(token="ghp_TEST", _client=httpx.AsyncClient(transport=transport))
    return svc, transport


@pytest.mark.asyncio
async def test_get_user(client):
    svc, _ = client
    user = await svc.get_authenticated_user()
    assert user["login"] == "MochiiAI"


@pytest.mark.asyncio
async def test_create_pull_request(client):
    svc, _ = client
    pr = await svc.create_pull_request(
        "MochiiAI/Mochi", title="Add widget", head="feat/widget", base="main"
    )
    assert pr["number"] == 42


@pytest.mark.asyncio
async def test_create_issue(client):
    svc, _ = client
    issue = await svc.create_issue("MochiiAI/Mochi", title="Bug", body="It breaks")
    assert issue["number"] == 7


@pytest.mark.asyncio
async def test_missing_token_raises(client):
    svc, _ = client
    svc.token = None
    with pytest.raises(GitHubServiceError):
        await svc.get_authenticated_user()


@pytest.mark.asyncio
async def test_error_propagates(client):
    svc, _ = client
    with pytest.raises(GitHubServiceError):
        await svc.get_issue("Nope/Nope", 1)