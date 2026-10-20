"""Service layer: GitHub REST integration, LLM providers, task execution."""

from app.services.github import GitHubService, GitHubServiceError

__all__ = ["GitHubService", "GitHubServiceError"]