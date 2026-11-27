"""Pydantic v2 schemas for the Drafty. API (strict, no extra fields)."""
from __future__ import annotations

from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field

_STRICT = ConfigDict(extra="forbid")


class ContentMetadata(BaseModel):
    model_config = _STRICT
    target_platforms: list[str] = Field(description="e.g. TikTok, Instagram Reels, LinkedIn")
    content_pillar: str
    tone: str


class ContentAssets(BaseModel):
    model_config = _STRICT
    hook_options: list[str] = Field(description="Exactly 3 scroll-stopping hooks")
    primary_caption: str = Field(description="Caption or full script, with line breaks")
    cta: str
    hashtags: list[str]


class Shot(BaseModel):
    model_config = _STRICT
    timestamp: str = Field(description="e.g. '0-3s'")
    description: str


class CreativeDirection(BaseModel):
    model_config = _STRICT
    visual_type: str
    shot_list: list[Shot]
    text_overlays: list[str]


class Schedule(BaseModel):
    model_config = _STRICT
    recommended_day: str = Field(description="Weekday name, e.g. Friday")
    recommended_date: str = Field(description="YYYY-MM-DD, in the future")
    recommended_time: str = Field(description="24h HH:MM")
    peak_window_reason: str


class ParsedContent(BaseModel):
    """Schema the LLM must fill (Structured Outputs). No server-owned fields."""
    model_config = _STRICT
    title: str
    summary: str
    metadata: ContentMetadata
    assets: ContentAssets
    creative_direction: CreativeDirection
    schedule: Schedule


class IdeaContainer(ParsedContent):
    """Final API response: parsed content + server-assigned id."""
    id: UUID = Field(default_factory=uuid4)
    draft_id: UUID | None = None


class DraftedSyncRequest(BaseModel):
    model_config = _STRICT
    title: str | None = None
    assets: ContentAssets | None = None
    schedule: Schedule | None = None
    selected_hook_index: int = 0


class ContainerUpdateRequest(BaseModel):
    model_config = _STRICT
    title: str | None = None
    summary: str | None = None
    assets: ContentAssets | None = None
    creative_direction: CreativeDirection | None = None
    schedule: Schedule | None = None


class ContainerDeleteResponse(BaseModel):
    container_id: UUID
    deleted: bool = True


class DraftedSyncResponse(BaseModel):
    container_id: UUID
    scheduled_for: str | None = Field(description="ISO local datetime, e.g. 2026-11-06T18:00")
    sync_status: str
    drafted_post_id: str | None = None


class AudioUploadResponse(BaseModel):
    storage_path: str
    bytes_received: int
    content_type: str



class UserProfileRequest(BaseModel):
    model_config = _STRICT
    user_id: UUID | None = None
    email: str = Field(min_length=3)
    brand_name: str = Field(min_length=1)
    brand_voice_prompt: str = ""


class UserProfileResponse(BaseModel):
    id: UUID
    email: str
    brand_name: str
    brand_voice_prompt: str


class RegenerateHooksRequest(BaseModel):
    model_config = _STRICT
    title: str = Field(min_length=1)
    summary: str = ""
    primary_caption: str = ""
    existing_hooks: list[str] = Field(default_factory=list)
    brand_voice: str = ""


class RegenerateHooksResponse(BaseModel):
    model_config = _STRICT
    hook_options: list[str] = Field(min_length=3, max_length=3)


class ErrorResponse(BaseModel):
    detail: str
    code: str

