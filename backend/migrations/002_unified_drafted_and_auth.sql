-- =============================================================================
-- Drafty + Drafted Unified Supabase Migration (002)
-- Run this in Drafty's Supabase SQL Editor to:
--   1) Add Drafted's tables (posts, subtasks, radar_events, checklist_presets, milestone_stickers)
--   2) Link user_profiles to Supabase Auth (auth.users) with an auto-profile trigger
--   3) Add drafted_post_id to content_containers for idempotent Drafty -> Drafted sync
--   4) Configure unified Row-Level Security (RLS) across both apps
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. Link Drafty content_containers to Drafted posts
-- -----------------------------------------------------------------------------
ALTER TABLE public.content_containers
    ADD COLUMN IF NOT EXISTS drafted_post_id TEXT;

CREATE INDEX IF NOT EXISTS idx_containers_drafted_post_id
    ON public.content_containers (drafted_post_id);

-- -----------------------------------------------------------------------------
-- 2. Drafted Tables (migrated into Drafty's canonical Supabase project)
-- -----------------------------------------------------------------------------

-- 2a. Posts Table
CREATE TABLE IF NOT EXISTS public.posts (
    id                  TEXT PRIMARY KEY,
    user_id             TEXT NOT NULL DEFAULT 'local_device',
    title               TEXT NOT NULL,
    platform            TEXT NOT NULL,
    platforms           TEXT[] NOT NULL DEFAULT '{}',
    format_type         TEXT NOT NULL,
    scheduled_at        TIMESTAMPTZ,
    is_backlog          BOOLEAN NOT NULL DEFAULT FALSE,
    status              TEXT NOT NULL DEFAULT 'idea',
    display_time_label  TEXT,
    hook_preview        TEXT,
    progress            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    quick_chips         TEXT[] NOT NULL DEFAULT '{}',
    backlog_stage_label TEXT,
    caption_text        TEXT,
    hashtags            TEXT,
    external_asset_url  TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_posts_user_scheduled
    ON public.posts (user_id, scheduled_at);

CREATE INDEX IF NOT EXISTS idx_posts_user_backlog
    ON public.posts (user_id, is_backlog);

-- 2b. Subtasks Table
CREATE TABLE IF NOT EXISTS public.subtasks (
    id                   TEXT PRIMARY KEY,
    post_id              TEXT NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id              TEXT NOT NULL DEFAULT 'local_device',
    title                TEXT NOT NULL,
    phase                TEXT NOT NULL DEFAULT 'pre_prod',
    is_completed         BOOLEAN NOT NULL DEFAULT FALSE,
    relative_offset_days INTEGER NOT NULL DEFAULT 0,
    order_index          INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subtasks_post_order
    ON public.subtasks (post_id, order_index);

-- 2c. Radar Events Table
CREATE TABLE IF NOT EXISTS public.radar_events (
    id                    TEXT PRIMARY KEY,
    user_id               TEXT NOT NULL DEFAULT 'local_device',
    title                 TEXT NOT NULL,
    date                  TIMESTAMPTZ NOT NULL,
    category              TEXT NOT NULL,
    default_lead_days     INTEGER NOT NULL DEFAULT 14,
    description           TEXT NOT NULL DEFAULT '',
    countdown_label       TEXT NOT NULL DEFAULT 'In 2 weeks',
    is_unread             BOOLEAN NOT NULL DEFAULT TRUE,
    is_converted_to_draft BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_radar_events_user_date
    ON public.radar_events (user_id, date);

-- 2d. Checklist Presets Table
CREATE TABLE IF NOT EXISTS public.checklist_presets (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL DEFAULT 'local_device',
    format_type TEXT NOT NULL,
    subtasks    JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, format_type)
);

-- 2e. Milestone Stickers Table (CAL-04)
CREATE TABLE IF NOT EXISTS public.milestone_stickers (
    id           TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL DEFAULT 'local_device',
    date_key     TEXT NOT NULL,
    sticker_type TEXT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, date_key)
);

CREATE INDEX IF NOT EXISTS idx_milestone_stickers_user_date
    ON public.milestone_stickers (user_id, date_key);

-- -----------------------------------------------------------------------------
-- 3. Auto-sync auth.users -> public.user_profiles (Single Account for Both Apps)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT := NULLIF(LOWER(TRIM(COALESCE(NEW.email, ''))), '');
    v_name  TEXT := COALESCE(
        NULLIF(TRIM(NEW.raw_user_meta_data->>'brand_name'), ''),
        NULLIF(TRIM(NEW.raw_user_meta_data->>'full_name'), ''),
        NULLIF(TRIM(NEW.raw_user_meta_data->>'name'), ''),
        SPLIT_PART(COALESCE(v_email, 'Creator'), '@', 1)
    );
    v_voice TEXT := COALESCE(NEW.raw_user_meta_data->>'brand_voice_prompt', '');
BEGIN
    -- Release email if an older pre-auth guest profile row was holding it
    IF v_email IS NOT NULL THEN
        UPDATE public.user_profiles
           SET email = NULL
         WHERE LOWER(email) = v_email
           AND id <> NEW.id;
    END IF;

    INSERT INTO public.user_profiles (id, email, brand_name, brand_voice_prompt)
    VALUES (NEW.id, v_email, v_name, v_voice)
    ON CONFLICT (id) DO UPDATE SET
        email = COALESCE(EXCLUDED.email, public.user_profiles.email),
        brand_name = COALESCE(NULLIF(public.user_profiles.brand_name, ''), EXCLUDED.brand_name);

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Never block Supabase Auth user creation if profile sync hits a transient constraint
    RAISE WARNING 'handle_new_auth_user skipped for %: %', NEW.id, SQLERRM;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- -----------------------------------------------------------------------------
-- 4. Unified Row-Level Security (RLS) Policies
-- -----------------------------------------------------------------------------
ALTER TABLE public.user_profiles      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_drafts       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_containers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subtasks           ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.radar_events       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.checklist_presets  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.milestone_stickers ENABLE ROW LEVEL SECURITY;

-- Drafty tables: authenticated users manage their own rows via auth.uid()
DROP POLICY IF EXISTS "Users manage own user_profiles" ON public.user_profiles;
CREATE POLICY "Users manage own user_profiles"
    ON public.user_profiles FOR ALL
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users manage own voice_drafts" ON public.voice_drafts;
CREATE POLICY "Users manage own voice_drafts"
    ON public.voice_drafts FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users manage own content_containers" ON public.content_containers;
CREATE POLICY "Users manage own content_containers"
    ON public.content_containers FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Drafted tables: authenticated users (auth.uid()::text) + local_device anon fallback
DROP POLICY IF EXISTS "Users manage own posts" ON public.posts;
CREATE POLICY "Users manage own posts"
    ON public.posts FOR ALL
    USING (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    )
    WITH CHECK (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    );

DROP POLICY IF EXISTS "Users manage own subtasks" ON public.subtasks;
CREATE POLICY "Users manage own subtasks"
    ON public.subtasks FOR ALL
    USING (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    )
    WITH CHECK (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    );

DROP POLICY IF EXISTS "Users manage own radar_events" ON public.radar_events;
CREATE POLICY "Users manage own radar_events"
    ON public.radar_events FOR ALL
    USING (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    )
    WITH CHECK (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    );

DROP POLICY IF EXISTS "Users manage own checklist_presets" ON public.checklist_presets;
CREATE POLICY "Users manage own checklist_presets"
    ON public.checklist_presets FOR ALL
    USING (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    )
    WITH CHECK (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    );

DROP POLICY IF EXISTS "Users manage own milestone_stickers" ON public.milestone_stickers;
CREATE POLICY "Users manage own milestone_stickers"
    ON public.milestone_stickers FOR ALL
    USING (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    )
    WITH CHECK (
        (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
        OR (auth.role() = 'anon' AND user_id = 'local_device')
    );
