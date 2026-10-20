"""Long-running task executor.

Runs a cloud task: gather project context, call the configured LLM, dispatch
structured actions (open PR, create issue, fetch repo state) and stream
agent-state transitions back to the connected device over the WebSocket hub.
"""

from __future__ import annotations

import asyncio
import json
import logging
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.models import AgentState, Project, Task, TaskStatus
from app.schemas import LLMChatMessage
from app.services.github import GitHubService, GitHubServiceError
from app.services.llm import LLMProvider
from app.ws.hub import ConnectionHub

logger = logging.getLogger("mochi.executor")

TOOL_CALLING_SYSTEM_PROMPT = """You are Mochi, a highly capable hybrid AI coding agent.
You reason through coding problems and, when a repository is attached, you use the
GitHub REST API to gather context. Respond with a valid JSON object using exactly one
of these shapes:

{"action": "reply", "text": "human-readable answer"}
{"action": "open_pr", "title": "...", "head": "...", "base": "main", "body": "..."}
{"action": "create_issue", "title": "...", "body": "...", "labels": ["..."], "repo": "owner/name"}
{"action": "fetch_issues", "repo": "owner/name", "state": "open", "limit": 20}

Prefer reply whenever the user is simply chatting with you."""


def _normalise_fence(content: str) -> str:
    """Strip a markdown code fence if the LLM wrapped its JSON output."""
    text = content.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines)
    return text.strip()


class LLMOutputError(RuntimeError):
    pass


class TaskExecutor:
    """Owns execution of one task instance."""

    def __init__(
        self,
        task: Task,
        db: Session,
        hub: ConnectionHub,
        provider: LLMProvider,
        github: GitHubService,
    ) -> None:
        self.task = task
        self.db = db
        self.hub = hub
        self.provider = provider
        self.github = github

    # -- state broadcasting --------------------------------------------------
    async def _set_state(
        self,
        state: AgentState,
        *,
        action_name: str | None = None,
        action_detail: str | None = None,
        message: str | None = None,
    ) -> None:
        await self.hub.publish_agent_state(
            device_id=self.task.device_id,
            state=state,
            action_name=action_name,
            action_detail=action_detail,
            message=message,
        )

    async def _push(self, event: str, payload: dict[str, Any]) -> None:
        await self.hub.publish_event(self.task.device_id, event, payload)

    async def _commit(self) -> None:
        self.db.commit()
        self.db.refresh(self.task)

    # -- context gathering ---------------------------------------------------
    async def _context_lines(self) -> str:
        if not self.task.project_id:
            return ""
        project = self.db.scalar(
            select(Project)
            .options(selectinload(Project.tasks))
            .where(Project.id == self.task.project_id)
        )
        if project is None:
            return ""
        try:
            repo = await self.github.get_repository(project.repository_full_name)
        except GitHubServiceError:
            return ""
        lines = [
            "REPOSITORY CONTEXT:",
            f"- Full Name: {repo.get('full_name')}",
            f"- Default branch: {repo.get('default_branch')}",
            f"- Language: {repo.get('language')}",
            f"- Description: {repo.get('description') or 'n/a'}",
            f"- Open issues: {repo.get('open_issues_count', 'n/a')}",
        ]
        try:
            issues = await self.github.list_issues(
                project.repository_full_name, state="open", limit=10
            )
        except GitHubServiceError:
            issues = []
        if issues:
            lines.append("- Recent open issues:")
            for issue in issues[:10]:
                if "pull_request" in issue:
                    continue  # the issues endpoint also returns PRs
                lines.append(f"  - #{issue['number']}: {issue['title']}")
        return "\n".join(lines)

    # -- action dispatch -------------------------------------------------------
    async def _dispatch(self, action: dict[str, Any]) -> str:
        kind = action.get("action")
        if kind == "reply":
            return str(action.get("text", ""))

        if kind == "open_pr":
            await self._set_state(
                AgentState.ACTION,
                action_name="Opening pull request",
                message=action.get("title", ""),
            )
            target = await self._target_repo(action.get("repo"))
            pr = await self.github.create_pull_request(
                target,
                title=str(action["title"]),
                head=str(action["head"]),
                base=str(action.get("base", "main")),
                body=action.get("body"),
                draft=bool(action.get("draft", False)),
            )
            await self._push(
                "github.pr_created",
                {"url": pr.get("html_url"), "number": pr.get("number")},
            )
            return f"Opened pull request {pr.get('html_url')}"

        if kind == "create_issue":
            await self._set_state(
                AgentState.ACTION, action_name="Creating issue", message=action.get("title", "")
            )
            target = await self._target_repo(action.get("repo"))
            issue = await self.github.create_issue(
                target,
                title=str(action["title"]),
                body=str(action.get("body", "")),
                labels=action.get("labels"),
            )
            await self._push(
                "github.issue_created",
                {"url": issue.get("html_url"), "number": issue.get("number")},
            )
            return f"Created issue {issue.get('html_url')}"

        if kind == "fetch_issues":
            await self._set_state(
                AgentState.ACTION, action_name="Fetching issues", message=action.get("repo", "")
            )
            target = await self._target_repo(action.get("repo"))
            issues = [
                {"number": i["number"], "title": i["title"], "html_url": i.get("html_url")}
                for i in await self.github.list_issues(
                    target,
                    state=str(action.get("state", "open")),
                    limit=int(action.get("limit", 20)),
                )
                if "pull_request" not in i
            ]
            await self._push("github.issues", {"issues": issues})
            return f"Fetched {len(issues)} issues."

        raise LLMOutputError(f"Unknown action: {kind}")

    async def _target_repo(self, repo: str | None) -> str:
        if repo:
            return repo
        if self.task.project_id:
            project = self.db.get(Project, self.task.project_id)
            if project is not None:
                return project.repository_full_name
        raise GitHubServiceError("No repository context available for this action.")

    # -- main entry point ----------------------------------------------------
    async def run(self) -> Task:
        log_ctx = {"task_id": self.task.id, "device_id": self.task.device_id}
        try:
            await self._set_state(AgentState.THINKING, message=self.task.title)

            context = await self._context_lines()
            messages = [
                LLMChatMessage(role="system", content=TOOL_CALLING_SYSTEM_PROMPT),
                *([LLMChatMessage(role="system", content=context)] if context else []),
                LLMChatMessage(role="user", content=self.task.prompt),
            ]

            result = await self.provider.complete(messages, temperature=0.4, max_tokens=4096)
            logger.info("llm complete", extra={**log_ctx, "provider": result.provider})

            action = self._parse_action(result.content)
            await self._set_state(
                AgentState.ACTION, action_name=f"Executing: {action.get('action')}"
            )

            answer = await self._dispatch(action)
            self.task.result = answer
            self.task.status = TaskStatus.SUCCEEDED
            self.task.finished_at = datetime.now(UTC)
            await self._set_state(AgentState.IDLE, message="Done")
            await self._push("task.completed", {"task_id": self.task.id, "result": answer})
        except Exception as exc:  # noqa: BLE001 - record any failure
            logger.exception("task failed", extra=log_ctx)
            self.task.status = TaskStatus.FAILED
            self.task.error = str(exc)
            self.task.finished_at = datetime.now(UTC)
            await self._set_state(AgentState.IDLE, message="Task failed")
            await self._push("task.failed", {"task_id": self.task.id, "error": str(exc)})
        finally:
            await self._commit()
        return self.task

    @staticmethod
    def _parse_action(content: str) -> dict[str, Any]:
        text = _normalise_fence(content)
        # The model may answer conversationally without JSON when no tool is needed.
        if not (text.startswith("{") and text.endswith("}")):
            return {"action": "reply", "text": text}
        try:
            action = json.loads(text)
        except json.JSONDecodeError as exc:
            raise LLMOutputError(f"LLM returned invalid JSON: {text[:300]}") from exc
        if not isinstance(action, dict) or "action" not in action:
            raise LLMOutputError(f"LLM output missing 'action' key: {text[:300]}")
        return action


async def run_task(
    task: Task,
    *,
    db: Session,
    hub: ConnectionHub,
    provider: LLMProvider,
    github: GitHubService,
    timeout_seconds: float = 300.0,
) -> Task:
    """Execute an already-persisted task, guaranteeing a terminal status.

    The caller is responsible for creating and committing the ``Task`` row,
    then calling this with a session bound to that row.
    """
    executor = TaskExecutor(task=task, db=db, hub=hub, provider=provider, github=github)
    try:
        db.refresh(task)
        await asyncio.wait_for(executor.run(), timeout=timeout_seconds)
    except TimeoutError:
        task.status = TaskStatus.FAILED
        task.error = "Task timed out"
        task.finished_at = datetime.now(UTC)
        db.commit()
    except Exception as exc:  # noqa: BLE001 - guarantee terminal state
        task.status = TaskStatus.FAILED
        task.error = str(exc)
        task.finished_at = datetime.now(UTC)
        db.commit()
    return task