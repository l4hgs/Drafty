"""Centralized configuration and environment variable loading for Drafty. backend."""
from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

ENV_PATH = Path(__file__).resolve().parent / ".env"
load_dotenv(dotenv_path=ENV_PATH)


class Settings:
    """Dynamic settings accessor that honors runtime os.environ overrides."""

    @property
    def OPENAI_API_KEY(self) -> str | None:
        return os.getenv("OPENAI_API_KEY")

    @property
    def WHISPER_MODEL(self) -> str:
        return os.getenv("WHISPER_MODEL", "whisper-1")

    @property
    def PARSER_MODEL(self) -> str:
        return os.getenv("PARSER_MODEL", "gpt-4o-mini")

    @property
    def SUPABASE_URL(self) -> str | None:
        return os.getenv("SUPABASE_URL")

    @property
    def SUPABASE_KEY(self) -> str | None:
        return os.getenv("SUPABASE_KEY")

    @property
    def SUPABASE_AUDIO_BUCKET(self) -> str:
        return os.getenv("SUPABASE_AUDIO_BUCKET", "voice-drafts")

    @property
    def MAX_AUDIO_BYTES(self) -> int:
        return int(os.getenv("MAX_AUDIO_BYTES", 25 * 1024 * 1024))

    @property
    def MIN_AUDIO_BYTES(self) -> int:
        return int(os.getenv("MIN_AUDIO_BYTES", 2048))

    @property
    def CORS_ORIGINS(self) -> list[str]:
        return [o.strip() for o in os.getenv("CORS_ORIGINS", "*").split(",") if o.strip()] or ["*"]

    @property
    def LOG_LEVEL(self) -> str:
        return os.getenv("LOG_LEVEL", "INFO")

    @property
    def REQUIRE_AUTH(self) -> bool:
        return os.getenv("REQUIRE_AUTH", "false").strip().lower() in ("1", "true", "yes")


settings = Settings()

OPENAI_API_KEY = settings.OPENAI_API_KEY
WHISPER_MODEL = settings.WHISPER_MODEL
PARSER_MODEL = settings.PARSER_MODEL
SUPABASE_URL = settings.SUPABASE_URL
SUPABASE_KEY = settings.SUPABASE_KEY
SUPABASE_AUDIO_BUCKET = settings.SUPABASE_AUDIO_BUCKET
MAX_AUDIO_BYTES = settings.MAX_AUDIO_BYTES
MIN_AUDIO_BYTES = settings.MIN_AUDIO_BYTES
CORS_ORIGINS = settings.CORS_ORIGINS
LOG_LEVEL = settings.LOG_LEVEL
