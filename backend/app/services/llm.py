"""Cloud LLM inference providers (OpenAI / Anthropic) with a tiny interface.

The heavy inference work happens here on the server; the Android app stays
light or runs its embedded local model (llama.cpp/MLC-LLM via JNI) offline.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any

import httpx

from app.config import settings
from app.schemas import LLMChatMessage


class LLMError(RuntimeError):
    pass


@dataclass
class LLMResult:
    provider: str
    model: str
    content: str
    usage: dict[str, int]


class LLMProvider(ABC):
    name: str
    model: str

    @abstractmethod
    async def complete(
        self, messages: list[LLMChatMessage], *, temperature: float, max_tokens: int | None
    ) -> LLMResult:
        raise NotImplementedError


class OpenAIProvider(LLMProvider):
    name = "openai"
    model = settings.openai_model

    def __init__(
        self, api_key: str | None = None, base_url: str = "https://api.openai.com/v1"
    ) -> None:
        self.api_key = api_key or settings.openai_api_key
        self.base_url = base_url

    async def complete(self, messages, *, temperature, max_tokens) -> LLMResult:
        if not self.api_key:
            raise LLMError("OPENAI_API_KEY is not configured")
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": [m.model_dump() for m in messages],
            "temperature": temperature,
        }
        if max_tokens is not None:
            payload["max_tokens"] = max_tokens
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            )
            if resp.status_code >= 400:
                raise LLMError(f"OpenAI API {resp.status_code}: {resp.text[:400]}")
            data = resp.json()
        choice = data["choices"][0]
        usage = data.get("usage") or {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
        return LLMResult(
            provider=self.name,
            model=data.get("model", self.model),
            content=choice["message"]["content"],
            usage=usage,
        )


class AnthropicProvider(LLMProvider):
    name = "anthropic"
    model = settings.anthropic_model

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key or settings.anthropic_api_key

    async def complete(self, messages, *, temperature, max_tokens) -> LLMResult:
        if not self.api_key:
            raise LLMError("ANTHROPIC_API_KEY is not configured")
        system = "\n".join(m.content for m in messages if m.role == "system") or None
        chat_messages = [
            {"role": m.role, "content": m.content}
            for m in messages
            if m.role in ("user", "assistant")
        ]
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": chat_messages,
            "temperature": temperature,
            "max_tokens": max_tokens or 4096,
        }
        if system:
            payload["system"] = system
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": self.api_key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                },
                json=payload,
            )
            if resp.status_code >= 400:
                raise LLMError(f"Anthropic API {resp.status_code}: {resp.text[:400]}")
            data = resp.json()
        text = "".join(block.get("text", "") for block in data.get("content", []))
        usage = data.get("usage") or {"input_tokens": 0, "output_tokens": 0}
        return LLMResult(
            provider=self.name,
            model=data.get("model", self.model),
            content=text,
            usage=usage,
        )


def get_provider(name: str | None) -> LLMProvider:
    """Resolve a provider by name, defaulting to whatever is configured."""
    if name == "openai":
        return OpenAIProvider()
    if name == "anthropic":
        return AnthropicProvider()
    if settings.anthropic_api_key:
        return AnthropicProvider()
    if settings.openai_api_key:
        return OpenAIProvider()
    raise LLMError("No LLM provider configured on the server.")