"""TaskExecutor unit tests with fake LLM + GitHub services and a live hub."""

from __future__ import annotations

import pytest

from app.models import AgentState, Task, TaskStatus
from app.services.executor import TaskExecutor, _normalise_fence
from app.services.github import GitHubServiceError
from app.ws.hub import ConnectionHub


class FakeLLM:
    def __init__(self, content: str):
        self.content = content
        self.calls = 0

    async def complete(self, messages, *, temperature, max_tokens=None):
        self.calls += 1
        from app.services.llm import LLMResult

        return LLMResult(provider="fake", model="fake", content=self.content, usage={})


class FakeGitHub:
    def __init__(self):
        self.created = []

    async def get_repository(self, name):
        return {"full_name": name, "default_branch": "main", "language": "Kotlin"}

    async def list_issues(self, name, *, state="open", limit=30):
        return [
            {"number": 1, "title": "hi", "pull_request": {}},
            {"number": 2, "title": "open issue"},
        ]

    async def create_pull_request(self, repo, *, title, head, base="main", body=None, draft=False):
        self.created.append((repo, title, head, base))
        return {"number": 99, "html_url": f"https://github.com/{repo}/pull/99"}

    async def create_issue(self, repo, *, title, body="", labels=None):
        return {"number": 5, "html_url": f"https://github.com/{repo}/issues/5"}


def _task(db, title="t", prompt="p"):
    task = Task(device_id="dev", title=title, prompt=prompt, status=TaskStatus.PENDING)
    db.add(task)
    db.commit()
    db.refresh(task)
    return task


async def _run(db, task, content: str):
    hub = ConnectionHub()
    executor = TaskExecutor(
        task=task,
        db=db,
        hub=hub,
        provider=FakeLLM(content),
        github=FakeGitHub(),
    )
    return await executor.run(), hub


@pytest.mark.asyncio
async def test_reply_action(db_session):
    task = _task(db_session, prompt="hi")
    finished, hub = await _run(db_session, task, '{"action": "reply", "text": "Hello!"}')
    assert finished.status == TaskStatus.SUCCEEDED
    assert finished.result == "Hello!"
    assert hub.current_states["dev"] == AgentState.IDLE


@pytest.mark.asyncio
async def test_open_pr_action_with_repo_context(db_session):
    from app.models import Project

    task = _task(db_session, prompt="open pr")
    project = Project(device_id="dev", repository_full_name="MochiiAI/Mochi", branch="main")
    db_session.add(project)
    db_session.flush()
    task.project_id = project.id
    db_session.commit()
    db_session.refresh(task)

    finished, _ = await _run(
        db_session,
        task,
        '{"action": "open_pr", "title": "Fix", "head": "fix/x", "base": "main"}',
    )
    assert finished.status == TaskStatus.SUCCEEDED
    assert "pull/99" in finished.result


@pytest.mark.asyncio
async def test_conversational_non_json_falls_back_to_reply(db_session):
    task = _task(db_session, prompt="chat")
    finished, _ = await _run(db_session, task, "Sure, I can help you with that!")
    assert finished.status == TaskStatus.SUCCEEDED
    assert finished.result == "Sure, I can help you with that!"


@pytest.mark.asyncio
async def test_fenced_json_is_parsed(db_session):
    task = _task(db_session, prompt="fenced")
    finished, _ = await _run(
        db_session,
        task,
        '```json\n{"action": "reply", "text": "inside fence"}\n```',
    )
    assert finished.result == "inside fence"


@pytest.mark.asyncio
async def test_unknown_action_fails_gracefully(db_session):
    task = _task(db_session, prompt="bad")
    finished, _ = await _run(db_session, task, '{"action": "explode"}')
    assert finished.status == TaskStatus.FAILED
    assert "Unknown action" in finished.error


@pytest.mark.asyncio
async def test_github_error_marks_failed(db_session):
    from app.models import Project

    task = _task(db_session, prompt="pr on attached repo")
    project = Project(device_id="dev", repository_full_name="MochiiAI/Mochi")
    db_session.add(project)
    db_session.flush()
    task.project_id = project.id
    db_session.commit()
    db_session.refresh(task)

    class FailingGitHub(FakeGitHub):
        async def create_pull_request(self, *args, **kwargs):
            raise GitHubServiceError("boom")

    hub = ConnectionHub()
    executor = TaskExecutor(
        task=task,
        db=db_session,
        hub=hub,
        provider=FakeLLM('{"action": "open_pr", "title": "T", "head": "h", "base": "b"}'),
        github=FailingGitHub(),
    )
    finished = await executor.run()
    assert finished.status == TaskStatus.FAILED
    assert "boom" in (finished.error or "")


def test_normalise_fence():
    assert _normalise_fence("```json\n{\"a\": 1}\n```") == '{"a": 1}'
    assert _normalise_fence('{"a": 1}') == '{"a": 1}'