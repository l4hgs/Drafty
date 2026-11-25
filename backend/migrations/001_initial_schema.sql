-- Drafty. initial schema (Supabase / PostgreSQL)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS user_profiles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              TEXT UNIQUE,
    brand_name         TEXT,
    brand_voice_prompt TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS voice_drafts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    audio_storage_path TEXT,
    raw_transcript     TEXT,
    duration_seconds   INT,
    status             TEXT NOT NULL DEFAULT 'pending'
                       CHECK (status IN ('pending', 'parsed', 'failed')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS content_containers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id             UUID NOT NULL REFERENCES voice_drafts(id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    title                TEXT NOT NULL,
    summary              TEXT,
    metadata             JSONB NOT NULL DEFAULT '{}'::jsonb,
    assets               JSONB NOT NULL DEFAULT '{}'::jsonb,
    creative_direction   JSONB NOT NULL DEFAULT '{}'::jsonb,
    schedule             JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_pushed_to_drafted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_voice_drafts_user_id        ON voice_drafts (user_id);
CREATE INDEX IF NOT EXISTS idx_voice_drafts_created_at     ON voice_drafts (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_containers_user_id          ON content_containers (user_id);
CREATE INDEX IF NOT EXISTS idx_containers_created_at       ON content_containers (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_containers_draft_id         ON content_containers (draft_id);
CREATE INDEX IF NOT EXISTS idx_containers_user_created     ON content_containers (user_id, created_at DESC);

-- Keep updated_at fresh on every update
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_containers_updated_at ON content_containers;
CREATE TRIGGER trg_containers_updated_at
    BEFORE UPDATE ON content_containers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- The backend uses the service-role key (bypasses RLS). Enabling RLS with no
-- policies blocks direct anon/authenticated access until policies are added.
ALTER TABLE user_profiles       ENABLE ROW LEVEL SECURITY;
ALTER TABLE voice_drafts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_containers  ENABLE ROW LEVEL SECURITY;

-- Private storage bucket for temporary/raw voice memos (.m4a / .wav)
INSERT INTO storage.buckets (id, name, public)
VALUES ('voice-drafts', 'voice-drafts', FALSE)
ON CONFLICT (id) DO NOTHING;


