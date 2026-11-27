-- =============================================================================
-- Drafty + Drafted Security Hardening Migration (003)
-- Run in Supabase SQL Editor once both Drafty and Drafted use Supabase Auth.
-- Removes anonymous `local_device` RLS access so every table strictly requires
-- an authenticated `auth.uid()`.
-- =============================================================================

DROP POLICY IF EXISTS "Users manage own posts" ON public.posts;
CREATE POLICY "Users manage own posts"
    ON public.posts FOR ALL
    USING (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
    WITH CHECK (auth.uid() IS NOT NULL AND auth.uid()::text = user_id);

DROP POLICY IF EXISTS "Users manage own subtasks" ON public.subtasks;
CREATE POLICY "Users manage own subtasks"
    ON public.subtasks FOR ALL
    USING (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
    WITH CHECK (auth.uid() IS NOT NULL AND auth.uid()::text = user_id);

DROP POLICY IF EXISTS "Users manage own radar_events" ON public.radar_events;
CREATE POLICY "Users manage own radar_events"
    ON public.radar_events FOR ALL
    USING (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
    WITH CHECK (auth.uid() IS NOT NULL AND auth.uid()::text = user_id);

DROP POLICY IF EXISTS "Users manage own checklist_presets" ON public.checklist_presets;
CREATE POLICY "Users manage own checklist_presets"
    ON public.checklist_presets FOR ALL
    USING (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
    WITH CHECK (auth.uid() IS NOT NULL AND auth.uid()::text = user_id);

DROP POLICY IF EXISTS "Users manage own milestone_stickers" ON public.milestone_stickers;
CREATE POLICY "Users manage own milestone_stickers"
    ON public.milestone_stickers FOR ALL
    USING (auth.uid() IS NOT NULL AND auth.uid()::text = user_id)
    WITH CHECK (auth.uid() IS NOT NULL AND auth.uid()::text = user_id);
