"""Unified chat/completion proxy over the configured cloud providers."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status

from app.models import Device
from app.schemas import LLMChatRequest, LLMChatResponse
from app.security import get_current_device
from app.services.llm import LLMError, get_provider

router = APIRouter(prefix="/api/llm", tags=["llm"])


@router.post("/chat", response_model=LLMChatResponse)
async def chat(
    payload: LLMChatRequest,
    _device: Device = Depends(get_current_device),
) -> LLMChatResponse:
    try:
        provider = get_provider(payload.provider)
        result = await provider.complete(
            payload.messages,
            temperature=payload.temperature,
            max_tokens=payload.max_tokens,
        )
    except LLMError as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)) from exc
    return LLMChatResponse(
        provider=result.provider,
        model=result.model,
        content=result.content,
        usage=result.usage,
    )