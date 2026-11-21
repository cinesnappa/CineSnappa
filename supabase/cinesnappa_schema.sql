-- =====================================================================
-- CineSnappa accounts & sync backend (Supabase / Postgres)
--
-- Paste this whole file into Supabase > SQL Editor > New query > Run.
-- Safe to run again: every object is created "if not exists" or replaced.
--
-- Matches the RPC calls made by the app (CineSnappa fork of Nuvio Mobile):
-- profiles + PINs, addons, library, watch progress (+delta), watched items
-- (+delta), settings blobs, home catalog settings, collections, and the
-- realtime "sync_invalidations" feed. Every function works only on the
-- signed-in user's own rows (auth.uid()).
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
    id uuid not null default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_index int not null,
    name text not null default '',
    avatar_color_hex text not null default '#E50914',
    avatar_id text,
    avatar_url text,
    uses_primary_addons boolean not null default false,
    uses_primary_plugins boolean not null default false,
    pin_hash text,
    pin_failed_attempts int not null default 0,
    pin_locked_until timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_index)
);

create table if not exists public.addons (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    url text not null,
    name text,
    enabled boolean not null default true,
    sort_order int not null default 0,
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id, url)
);

create table if not exists public.library_items (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    content_type text not null,
    content_id text not null,
    added_at bigint not null default 0,
    data jsonb not null,
    primary key (user_id, profile_id, content_type, content_id)
);

create table if not exists public.watch_progress (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    progress_key text not null,
    data jsonb not null,
    last_watched bigint not null default 0,
    primary key (user_id, profile_id, progress_key)
);

create table if not exists public.watch_progress_events (
    id bigserial primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    operation text not null,
    progress_key text not null,
    data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index if not exists watch_progress_events_lookup on public.watch_progress_events (user_id, profile_id, id);

create table if not exists public.watched_items (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    item_key text not null,
    data jsonb not null,
    watched_at bigint not null default 0,
    primary key (user_id, profile_id, item_key)
);

create table if not exists public.watched_items_events (
    id bigserial primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    operation text not null,
    item_key text not null,
    data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index if not exists watched_items_events_lookup on public.watched_items_events (user_id, profile_id, id);

create table if not exists public.settings_blobs (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    platform text not null,
    settings_json jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id, platform)
);

create table if not exists public.home_catalog_settings (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    platform text not null,
    settings_json jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id, platform)
);

create table if not exists public.collections (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    collections_json jsonb not null default '[]'::jsonb,
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id)
);

create table if not exists public.provider_credentials (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int not null,
    provider text not null,
    data jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id, provider)
);

create table if not exists public.avatar_catalog (
    id text primary key,
    display_name text not null default '',
    storage_path text not null default '',
    category text not null default 'character',
    sort_order int not null default 0,
    is_active boolean not null default true,
    bg_color text
);

create table if not exists public.sync_invalidations (
    id bigserial primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id int,
    surface text not null,
    origin_client_id text,
    created_at timestamptz not null default now()
);
create index if not exists sync_invalidations_user on public.sync_invalidations (user_id, id);

-- ---------------------------------------------------------------------
-- Row level security: users can only ever read their own rows.
-- Writes go through the security-definer functions below.
-- ---------------------------------------------------------------------
do $$
declare t text;
begin
    foreach t in array array[
        'profiles','addons','library_items','watch_progress','watch_progress_events',
        'watched_items','watched_items_events','settings_blobs','home_catalog_settings',
        'collections','provider_credentials','sync_invalidations'
    ] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('drop policy if exists "own rows" on public.%I', t);
        execute format('create policy "own rows" on public.%I for select to authenticated using (user_id = auth.uid())', t);
    end loop;
end $$;

alter table public.avatar_catalog enable row level security;
drop policy if exists "anyone can read" on public.avatar_catalog;
create policy "anyone can read" on public.avatar_catalog for select using (true);

-- Realtime feed the app listens to for cross-device refreshes.
do $$
begin
    alter publication supabase_realtime add table public.sync_invalidations;
exception when duplicate_object then null;
end $$;

-- ---------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------
create or replace function public._cs_uid() returns uuid
language plpgsql stable security definer set search_path = public as $$
declare uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'Not signed in' using errcode = '28000';
    end if;
    return uid;
end $$;

create or replace function public._cs_notify(p_surface text, p_profile_id int, p_origin text)
returns void language sql security definer set search_path = public as $$
    insert into public.sync_invalidations (user_id, profile_id, surface, origin_client_id)
    values (auth.uid(), p_profile_id, p_surface, p_origin);
    delete from public.sync_invalidations
    where user_id = auth.uid() and created_at < now() - interval '2 days';
$$;

create or replace function public._cs_watched_key(p jsonb) returns text
language sql immutable as $$
    select coalesce(p->>'content_id', '') || '|' ||
           coalesce(p->>'season', '-') || '|' ||
           coalesce(p->>'episode', '-');
$$;

-- ---------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------
create or replace function public._cs_profile_json(p public.profiles) returns jsonb
language sql stable as $$
    select jsonb_build_object(
        'id', p.id, 'user_id', p.user_id, 'profile_index', p.profile_index, 'name', p.name,
        'avatar_color_hex', p.avatar_color_hex, 'avatar_id', p.avatar_id, 'avatar_url', p.avatar_url,
        'uses_primary_addons', p.uses_primary_addons, 'uses_primary_plugins', p.uses_primary_plugins,
        'pin_enabled', p.pin_hash is not null, 'pin_locked_until', p.pin_locked_until,
        'created_at', p.created_at, 'updated_at', p.updated_at);
$$;

create or replace function public.sync_pull_profiles() returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    if not exists (select 1 from public.profiles where user_id = uid) then
        insert into public.profiles (user_id, profile_index, name) values (uid, 1, 'Main')
        on conflict do nothing;
    end if;
    return query select public._cs_profile_json(p) from public.profiles p
        where p.user_id = uid order by p.profile_index;
end $$;

create or replace function public.sync_push_profiles(
    p_client_max_profiles int, p_profiles jsonb, p_origin_client_id text default null
) returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid(); item jsonb; kept int[] := '{}';
begin
    for item in select * from jsonb_array_elements(coalesce(p_profiles, '[]'::jsonb))
        limit greatest(coalesce(p_client_max_profiles, 5), 1)
    loop
        insert into public.profiles as p (user_id, profile_index, name, avatar_color_hex, avatar_id, avatar_url,
            uses_primary_addons, uses_primary_plugins, updated_at)
        values (uid, (item->>'profile_index')::int, coalesce(item->>'name', ''),
            coalesce(item->>'avatar_color_hex', '#E50914'), item->>'avatar_id', item->>'avatar_url',
            coalesce((item->>'uses_primary_addons')::boolean, false),
            coalesce((item->>'uses_primary_plugins')::boolean, false), now())
        on conflict (user_id, profile_index) do update set
            name = excluded.name, avatar_color_hex = excluded.avatar_color_hex,
            avatar_id = excluded.avatar_id, avatar_url = excluded.avatar_url,
            uses_primary_addons = excluded.uses_primary_addons,
            uses_primary_plugins = excluded.uses_primary_plugins, updated_at = now();
        kept := kept || (item->>'profile_index')::int;
    end loop;
    if array_length(kept, 1) is not null then
        delete from public.profiles where user_id = uid and profile_index <> 1 and not (profile_index = any(kept));
    end if;
    perform public._cs_notify('profiles', null, p_origin_client_id);
end $$;

create or replace function public.sync_delete_profile_data(p_profile_id int, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    delete from public.addons where user_id = uid and profile_id = p_profile_id;
    delete from public.library_items where user_id = uid and profile_id = p_profile_id;
    delete from public.watch_progress where user_id = uid and profile_id = p_profile_id;
    delete from public.watch_progress_events where user_id = uid and profile_id = p_profile_id;
    delete from public.watched_items where user_id = uid and profile_id = p_profile_id;
    delete from public.watched_items_events where user_id = uid and profile_id = p_profile_id;
    delete from public.settings_blobs where user_id = uid and profile_id = p_profile_id;
    delete from public.home_catalog_settings where user_id = uid and profile_id = p_profile_id;
    delete from public.collections where user_id = uid and profile_id = p_profile_id;
    delete from public.provider_credentials where user_id = uid and profile_id = p_profile_id;
    if p_profile_id <> 1 then
        delete from public.profiles where user_id = uid and profile_index = p_profile_id;
    end if;
    perform public._cs_notify('profiles', null, p_origin_client_id);
end $$;

-- PINs (bcrypt-hashed; 5 wrong tries locks the profile for 5 minutes)
create or replace function public.verify_profile_pin(p_profile_id int, p_pin text) returns setof jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare uid uuid := public._cs_uid(); p public.profiles;
begin
    select * into p from public.profiles where user_id = uid and profile_index = p_profile_id;
    if not found or p.pin_hash is null then
        return query select jsonb_build_object('unlocked', true, 'retry_after_seconds', 0);
        return;
    end if;
    if p.pin_locked_until is not null and p.pin_locked_until > now() then
        return query select jsonb_build_object('unlocked', false,
            'retry_after_seconds', ceil(extract(epoch from p.pin_locked_until - now()))::int,
            'message', 'Too many attempts. Try again later.');
        return;
    end if;
    if crypt(p_pin, p.pin_hash) = p.pin_hash then
        update public.profiles set pin_failed_attempts = 0, pin_locked_until = null
            where user_id = uid and profile_index = p_profile_id;
        return query select jsonb_build_object('unlocked', true, 'retry_after_seconds', 0);
    else
        update public.profiles set
            pin_failed_attempts = pin_failed_attempts + 1,
            pin_locked_until = case when pin_failed_attempts + 1 >= 5 then now() + interval '5 minutes' else null end
            where user_id = uid and profile_index = p_profile_id;
        return query select jsonb_build_object('unlocked', false, 'retry_after_seconds', 0, 'message', 'Wrong PIN');
    end if;
end $$;

create or replace function public.set_profile_pin(p_profile_id int, p_pin text, p_current_pin text default null)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare uid uuid := public._cs_uid(); existing text;
begin
    select pin_hash into existing from public.profiles where user_id = uid and profile_index = p_profile_id;
    if existing is not null and (p_current_pin is null or crypt(p_current_pin, existing) <> existing) then
        raise exception 'Current PIN is incorrect';
    end if;
    update public.profiles set pin_hash = crypt(p_pin, gen_salt('bf')), pin_failed_attempts = 0,
        pin_locked_until = null, updated_at = now()
        where user_id = uid and profile_index = p_profile_id;
    perform public._cs_notify('profiles', null, null);
end $$;

create or replace function public.clear_profile_pin(p_profile_id int, p_current_pin text default null)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare uid uuid := public._cs_uid(); existing text;
begin
    select pin_hash into existing from public.profiles where user_id = uid and profile_index = p_profile_id;
    if existing is not null and (p_current_pin is null or crypt(p_current_pin, existing) <> existing) then
        raise exception 'Current PIN is incorrect';
    end if;
    update public.profiles set pin_hash = null, pin_failed_attempts = 0, pin_locked_until = null, updated_at = now()
        where user_id = uid and profile_index = p_profile_id;
    perform public._cs_notify('profiles', null, null);
end $$;

create or replace function public.clear_profile_pin_with_account_password(p_account_password text, p_profile_id int)
returns void language plpgsql security definer set search_path = public, extensions, auth as $$
declare uid uuid := public._cs_uid(); hash text;
begin
    select encrypted_password into hash from auth.users where id = uid;
    if hash is null or crypt(p_account_password, hash) <> hash then
        raise exception 'Account password is incorrect';
    end if;
    update public.profiles set pin_hash = null, pin_failed_attempts = 0, pin_locked_until = null, updated_at = now()
        where user_id = uid and profile_index = p_profile_id;
    perform public._cs_notify('profiles', null, null);
end $$;

create or replace function public.sync_pull_profile_locks() returns setof jsonb
language sql security definer set search_path = public as $$
    select jsonb_build_object('profile_index', profile_index, 'pin_enabled', pin_hash is not null,
        'pin_locked_until', pin_locked_until)
    from public.profiles where user_id = auth.uid() order by profile_index;
$$;

create or replace function public.get_avatar_catalog() returns setof jsonb
language sql security definer set search_path = public as $$
    select to_jsonb(a) from public.avatar_catalog a where a.is_active order by a.sort_order, a.id;
$$;

-- ---------------------------------------------------------------------
-- Addons (the app also reads public.addons directly)
-- ---------------------------------------------------------------------
create or replace function public.sync_push_addons(p_profile_id int, p_addons jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    delete from public.addons where user_id = uid and profile_id = p_profile_id;
    insert into public.addons (user_id, profile_id, url, name, enabled, sort_order)
    select distinct on (a->>'url') uid, p_profile_id, a->>'url', nullif(a->>'name', ''),
        coalesce((a->>'enabled')::boolean, true), coalesce((a->>'sort_order')::int, 0)
    from jsonb_array_elements(coalesce(p_addons, '[]'::jsonb)) a
    where coalesce(a->>'url', '') <> '';
    perform public._cs_notify('addons', p_profile_id, p_origin_client_id);
end $$;

-- ---------------------------------------------------------------------
-- Library
-- ---------------------------------------------------------------------
create or replace function public.sync_push_library(p_profile_id int, p_items jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    delete from public.library_items where user_id = uid and profile_id = p_profile_id;
    insert into public.library_items (user_id, profile_id, content_type, content_id, added_at, data)
    select distinct on (i->>'content_type', i->>'content_id') uid, p_profile_id, i->>'content_type', i->>'content_id',
        coalesce((i->>'added_at')::bigint, 0), i
    from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) i
    where coalesce(i->>'content_id', '') <> '';
    perform public._cs_notify('library', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_pull_library(p_profile_id int, p_limit int default 500, p_offset int default 0)
returns setof jsonb language sql security definer set search_path = public as $$
    select data from public.library_items
    where user_id = auth.uid() and profile_id = p_profile_id
    order by added_at desc, content_id
    limit coalesce(p_limit, 500) offset coalesce(p_offset, 0);
$$;

-- ---------------------------------------------------------------------
-- Watch progress (+ delta feed)
-- ---------------------------------------------------------------------
create or replace function public.sync_push_watch_progress(p_profile_id int, p_entries jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid(); e jsonb; k text;
begin
    for e in select * from jsonb_array_elements(coalesce(p_entries, '[]'::jsonb)) loop
        k := coalesce(nullif(e->>'progress_key', ''), (e->>'content_id') || '|' || coalesce(e->>'video_id', ''));
        e := e || jsonb_build_object('progress_key', k);
        insert into public.watch_progress (user_id, profile_id, progress_key, data, last_watched)
        values (uid, p_profile_id, k, e, coalesce((e->>'last_watched')::bigint, 0))
        on conflict (user_id, profile_id, progress_key) do update
            set data = excluded.data, last_watched = excluded.last_watched;
        insert into public.watch_progress_events (user_id, profile_id, operation, progress_key, data)
        values (uid, p_profile_id, 'upsert', k, e);
    end loop;
    perform public._cs_notify('watch_progress', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_delete_watch_progress(p_profile_id int, p_keys jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid(); k text;
begin
    for k in select jsonb_array_elements_text(coalesce(p_keys, '[]'::jsonb)) loop
        delete from public.watch_progress where user_id = uid and profile_id = p_profile_id and progress_key = k;
        insert into public.watch_progress_events (user_id, profile_id, operation, progress_key)
        values (uid, p_profile_id, 'delete', k);
    end loop;
    perform public._cs_notify('watch_progress', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_pull_watch_progress(
    p_profile_id int, p_since_last_watched bigint default null, p_limit int default null
) returns setof jsonb language sql security definer set search_path = public as $$
    select data from public.watch_progress
    where user_id = auth.uid() and profile_id = p_profile_id
      and (p_since_last_watched is null or last_watched > p_since_last_watched)
    order by last_watched desc
    limit coalesce(p_limit, 100000);
$$;

create or replace function public.sync_get_watch_progress_delta_cursor(p_profile_id int)
returns bigint language sql security definer set search_path = public as $$
    select coalesce(max(id), 0) from public.watch_progress_events
    where user_id = auth.uid() and profile_id = p_profile_id;
$$;

create or replace function public.sync_pull_watch_progress_delta(p_profile_id int, p_since_event_id bigint, p_limit int default 500)
returns setof jsonb language sql security definer set search_path = public as $$
    select ev.data || jsonb_build_object('event_id', ev.id, 'operation', ev.operation, 'progress_key', ev.progress_key)
    from public.watch_progress_events ev
    where ev.user_id = auth.uid() and ev.profile_id = p_profile_id and ev.id > coalesce(p_since_event_id, 0)
    order by ev.id
    limit coalesce(p_limit, 500);
$$;

-- ---------------------------------------------------------------------
-- Watched items (+ delta feed)
-- ---------------------------------------------------------------------
create or replace function public.sync_push_watched_items(p_profile_id int, p_items jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid(); i jsonb; k text;
begin
    for i in select * from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) loop
        k := public._cs_watched_key(i);
        insert into public.watched_items (user_id, profile_id, item_key, data, watched_at)
        values (uid, p_profile_id, k, i, coalesce((i->>'watched_at')::bigint, 0))
        on conflict (user_id, profile_id, item_key) do update set data = excluded.data, watched_at = excluded.watched_at;
        insert into public.watched_items_events (user_id, profile_id, operation, item_key, data)
        values (uid, p_profile_id, 'upsert', k, i);
    end loop;
    perform public._cs_notify('watched_items', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_delete_watched_items(p_profile_id int, p_keys jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid(); i jsonb; k text; old_data jsonb;
begin
    for i in select * from jsonb_array_elements(coalesce(p_keys, '[]'::jsonb)) loop
        k := public._cs_watched_key(i);
        delete from public.watched_items where user_id = uid and profile_id = p_profile_id and item_key = k
            returning data into old_data;
        insert into public.watched_items_events (user_id, profile_id, operation, item_key, data)
        values (uid, p_profile_id, 'delete', k, coalesce(old_data, i));
    end loop;
    perform public._cs_notify('watched_items', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_pull_watched_items(p_profile_id int, p_page int default 1, p_page_size int default 500)
returns setof jsonb language sql security definer set search_path = public as $$
    select data from public.watched_items
    where user_id = auth.uid() and profile_id = p_profile_id
    order by watched_at desc, item_key
    limit coalesce(p_page_size, 500) offset (greatest(coalesce(p_page, 1), 1) - 1) * coalesce(p_page_size, 500);
$$;

create or replace function public.sync_get_watched_items_delta_cursor(p_profile_id int)
returns bigint language sql security definer set search_path = public as $$
    select coalesce(max(id), 0) from public.watched_items_events
    where user_id = auth.uid() and profile_id = p_profile_id;
$$;

create or replace function public.sync_pull_watched_items_delta(p_profile_id int, p_since_event_id bigint, p_limit int default 500)
returns setof jsonb language sql security definer set search_path = public as $$
    select jsonb_build_object('content_id', '', 'content_type', '') || ev.data
        || jsonb_build_object('event_id', ev.id, 'operation', ev.operation)
    from public.watched_items_events ev
    where ev.user_id = auth.uid() and ev.profile_id = p_profile_id and ev.id > coalesce(p_since_event_id, 0)
    order by ev.id
    limit coalesce(p_limit, 500);
$$;

-- ---------------------------------------------------------------------
-- Settings, home catalogs, collections, provider credentials
-- ---------------------------------------------------------------------
create or replace function public.sync_push_profile_settings_blob(
    p_profile_id int, p_platform text, p_settings_json jsonb, p_origin_client_id text default null
) returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    insert into public.settings_blobs (user_id, profile_id, platform, settings_json, updated_at)
    values (uid, p_profile_id, p_platform, coalesce(p_settings_json, '{}'::jsonb), now())
    on conflict (user_id, profile_id, platform) do update set settings_json = excluded.settings_json, updated_at = now();
    perform public._cs_notify('profile_settings', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_pull_profile_settings_blob(p_profile_id int, p_platform text)
returns setof jsonb language sql security definer set search_path = public as $$
    select jsonb_build_object('profile_id', profile_id, 'settings_json', settings_json, 'updated_at', updated_at)
    from public.settings_blobs where user_id = auth.uid() and profile_id = p_profile_id and platform = p_platform;
$$;

create or replace function public.sync_push_home_catalog_settings(
    p_profile_id int, p_platform text, p_settings_json jsonb, p_origin_client_id text default null
) returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    insert into public.home_catalog_settings (user_id, profile_id, platform, settings_json, updated_at)
    values (uid, p_profile_id, p_platform, coalesce(p_settings_json, '{}'::jsonb), now())
    on conflict (user_id, profile_id, platform) do update set settings_json = excluded.settings_json, updated_at = now();
    perform public._cs_notify('home_catalog_settings', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_pull_home_catalog_settings(p_profile_id int, p_platform text)
returns setof jsonb language sql security definer set search_path = public as $$
    select jsonb_build_object('profile_id', profile_id, 'settings_json', settings_json, 'updated_at', updated_at)
    from public.home_catalog_settings where user_id = auth.uid() and profile_id = p_profile_id and platform = p_platform;
$$;

create or replace function public.sync_push_collections(p_profile_id int, p_collections_json jsonb, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    insert into public.collections (user_id, profile_id, collections_json, updated_at)
    values (uid, p_profile_id, coalesce(p_collections_json, '[]'::jsonb), now())
    on conflict (user_id, profile_id) do update set collections_json = excluded.collections_json, updated_at = now();
    perform public._cs_notify('collections', p_profile_id, p_origin_client_id);
end $$;

create or replace function public.sync_pull_collections(p_profile_id int)
returns setof jsonb language sql security definer set search_path = public as $$
    select jsonb_build_object('profile_id', profile_id, 'collections_json', collections_json, 'updated_at', updated_at)
    from public.collections where user_id = auth.uid() and profile_id = p_profile_id;
$$;

create or replace function public.sync_delete_provider_credentials(p_profile_id int, p_provider text, p_origin_client_id text default null)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := public._cs_uid();
begin
    delete from public.provider_credentials where user_id = uid and profile_id = p_profile_id and provider = p_provider;
end $$;

-- ---------------------------------------------------------------------
-- Permissions: only signed-in users may call the API functions.
-- ---------------------------------------------------------------------
revoke all on all functions in schema public from public, anon;
grant execute on all functions in schema public to authenticated;
grant select on public.addons, public.sync_invalidations, public.avatar_catalog to authenticated;
grant select on public.avatar_catalog to anon;

notify pgrst, 'reload schema';
