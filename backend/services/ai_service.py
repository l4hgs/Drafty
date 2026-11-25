"""Whisper transcription + GPT-4o mini structured parsing."""
from __future__ import annotations

import logging
from datetime import datetime

from openai import AsyncOpenAI, OpenAIError

from config import settings
from models import IdeaContainer, ParsedContent, RegenerateHooksRequest, RegenerateHooksResponse

logger = logging.getLogger("drafty.ai")

WHISPER_MODEL = settings.WHISPER_MODEL
PARSER_MODEL = settings.PARSER_MODEL
MIN_TRANSCRIPT_CHARS = 8

SYSTEM_PROMPT = """You are Drafty., an assistant that turns a creator's unscripted \
voice brain-dump into a structured multi-platform content container.

Rules:
- Use ONLY ideas present in the transcript. Do not invent facts, statistics, names or claims.
- Fix speech disfluencies; keep the speaker's voice.
- If a Creator brand voice is provided, tailor hooks, primary_caption, cta, and metadata.tone to match it.
- title: short working title. summary: 1-2 sentences.
- metadata.target_platforms: pick platforms implied by the transcript; otherwise choose 2-3 that fit.
- assets.hook_options: exactly 3 distinct hooks. primary_caption: ready-to-post caption or \
short video script using line breaks. hashtags: 3-6, each starting with '#'.
- creative_direction.shot_list: 3-6 shots with contiguous timestamps like '0-3s'.
- schedule: pick a strategic recommended_date 1 to 10 days strictly AFTER the provided current date. \
AVOID any dates listed under "Already scheduled dates" so the creator's calendar stays evenly spaced without double-booking. \
Match the weekday and 24h HH:MM recommended_time to the specific topic, content_pillar, tone, and target_platforms \
(e.g., educational/tech/career -> weekday morning 08:30 or lunch 12:15; gaming/comedy/entertainment -> Fri/Sat evening 20:30; \
lifestyle/reflection/wellness -> Sunday morning 10:30 or weeknight wind-down 21:00). \
recommended_day must be the true weekday of recommended_date, and peak_window_reason must be one specific, tailored sentence \
referencing the topic/platform (never a generic boilerplate line).
"""


class TranscriptionError(Exception):
    """Whisper failed."""


class EmptyTranscriptError(Exception):
    """Audio had no usable speech."""


class ParsingError(Exception):
    """LLM failed to produce schema-valid output."""


class AIService:
    def __init__(self, api_key: str | None = None) -> None:
        # Raises if no key is available; fail fast at startup.
        resolved_key = api_key or settings.OPENAI_API_KEY
        if not resolved_key:
            raise KeyError("OPENAI_API_KEY")
        self._client = AsyncOpenAI(api_key=resolved_key, timeout=20.0, max_retries=1)

    async def transcribe(self, audio: bytes, filename: str, content_type: str) -> str:
        try:
            result = await self._client.audio.transcriptions.create(
                model=WHISPER_MODEL,
                file=(filename, audio, content_type),
                response_format="text",
            )
        except OpenAIError as exc:
            logger.exception("Whisper failure")
            raise TranscriptionError(str(exc)) from exc
        text = (result if isinstance(result, str) else getattr(result, "text", "")).strip()
        logger.info("Whisper transcript (%d chars): %r", len(text), text)
        if len(text) < MIN_TRANSCRIPT_CHARS:
            raise EmptyTranscriptError(
                "We couldn't hear any speech. Hold Drafty and speak clearly into your mic."
            )
        return text

    async def parse(
        self,
        transcript: str,
        client_time: datetime,
        brand_voice: str | None = None,
        booked_dates: list[str] | None = None,
    ) -> ParsedContent:
        today = client_time.strftime("%A, %Y-%m-%d")
        voice_line = f"\nCreator brand voice: {brand_voice.strip()}" if brand_voice and brand_voice.strip() else ""
        booked_line = (
            f"\nAlready scheduled dates (pick a different open day): {', '.join(booked_dates)}"
            if booked_dates
            else ""
        )
        user_prompt = f"Current date: {today}{voice_line}{booked_line}\n\nTranscript:\n{transcript}"
        try:
            completion = await self._client.beta.chat.completions.parse(
                model=PARSER_MODEL,
                temperature=0.55,
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_prompt},
                ],
                response_format=ParsedContent,
            )
        except OpenAIError as exc:
            logger.exception("Parser failure")
            raise ParsingError(str(exc)) from exc
        message = completion.choices[0].message
        if message.refusal or message.parsed is None:
            raise ParsingError(message.refusal or "Model returned no parsed content.")
        return message.parsed

    async def regenerate_hooks(self, req: RegenerateHooksRequest) -> list[str]:
        voice_line = f"\nCreator brand voice: {req.brand_voice.strip()}" if req.brand_voice.strip() else ""
        avoid_line = (
            "\nAvoid repeating these existing hooks:\n- " + "\n- ".join(req.existing_hooks)
            if req.existing_hooks
            else ""
        )
        user_prompt = (
            f"Title: {req.title}\n"
            f"Summary: {req.summary}\n"
            f"Caption/Script: {req.primary_caption[:600]}"
            f"{voice_line}{avoid_line}\n\n"
            "Generate 3 fresh, distinct, scroll-stopping opening hooks (1 punchy sentence each) "
            "grounded strictly in the idea above."
        )
        try:
            completion = await self._client.beta.chat.completions.parse(
                model=PARSER_MODEL,
                temperature=0.8,
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "You are Drafty., a creative short-form content strategist. "
                            "Write 3 distinct, high-retention opening hooks without inventing facts."
                        ),
                    },
                    {"role": "user", "content": user_prompt},
                ],
                response_format=RegenerateHooksResponse,
            )
        except OpenAIError as exc:
            logger.exception("Regenerate hooks failure")
            raise ParsingError(str(exc)) from exc
        message = completion.choices[0].message
        if message.refusal or message.parsed is None:
            raise ParsingError(message.refusal or "Model returned no regenerated hooks.")
        return message.parsed.hook_options

    async def transcribe_and_parse(
        self,
        audio: bytes,
        filename: str,
        content_type: str,
        client_time: datetime,
        brand_voice: str | None = None,
    ) -> IdeaContainer:
        transcript = await self.transcribe(audio, filename, content_type)
        parsed = await self.parse(transcript, client_time, brand_voice=brand_voice)
        return IdeaContainer(**parsed.model_dump())

