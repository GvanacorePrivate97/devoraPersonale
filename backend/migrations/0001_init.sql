-- Men Care — schema iniziale.
-- Convenzioni:
--  * id uuid generati dal database; le app li trattano come stringhe opache
--  * ogni istante è timestamptz (UTC nel database); il fuso del salone
--    (Europe/Rome) vive in SALON_TIMEZONE e decide giorni e griglie orarie
--  * orari di lavoro e blocchi sono orari "da muro" (time), non istanti
--  * il denaro è in centesimi (bigint), mai in virgola mobile

create extension if not exists pgcrypto;   -- gen_random_uuid()
create extension if not exists btree_gist; -- vincolo di non sovrapposizione
create extension if not exists citext;     -- email senza distinzione maiuscole

create type user_role          as enum ('CLIENT', 'STAFF', 'OWNER');
create type appointment_status as enum ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW');
create type booking_channel    as enum ('APP', 'PHONE', 'WALK_IN');
create type cancellation_actor as enum ('CLIENT', 'SALON');
create type block_reason       as enum ('PERMESSO', 'PAUSA', 'FERIE', 'CORSO');
create type waitlist_status    as enum ('WAITING', 'NOTIFIED', 'EXPIRED');
create type campaign_segment   as enum ('INATTIVI_60', 'TUTTI', 'TOP_SPESA');
create type campaign_status    as enum ('DRAFT', 'SCHEDULED', 'SENT');
create type device_platform    as enum ('ANDROID', 'IOS');
create type notification_kind  as enum (
    'BOOKING_CONFIRMED', 'BOOKING_REMINDER', 'BOOKING_CANCELLED', 'BOOKING_RESCHEDULED',
    'WAITLIST_SLOT', 'CAMPAIGN', 'GENERIC'
);

-- Aggiorna updated_at a ogni UPDATE.
create or replace function touch_updated_at() returns trigger language plpgsql as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

-- ---------------------------------------------------------------- salone ----

-- Sede unica: una sola riga, garantita dal check su id.
create table salon (
    id         smallint primary key generated always as identity check (id = 1),
    name       text not null check (length(btrim(name)) > 0),
    address    text not null,
    city       text not null,
    phone      text,
    timezone   text not null default 'Europe/Rome',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create trigger salon_touch before update on salon
    for each row execute function touch_updated_at();

-- Orari di apertura. Più righe per giorno = turni spezzati (mattina/pomeriggio).
-- day_of_week segue ISO-8601: 1 = lunedì … 7 = domenica. Giorno assente = chiuso.
create table salon_hours (
    day_of_week smallint not null check (day_of_week between 1 and 7),
    starts_at   time not null,
    ends_at     time not null,
    constraint salon_hours_range check (starts_at < ends_at),
    primary key (day_of_week, starts_at)
);

create table services (
    id               uuid primary key default gen_random_uuid(),
    name             text not null check (length(btrim(name)) > 0),
    duration_minutes integer not null check (duration_minutes > 0 and duration_minutes <= 480),
    price_cents      bigint not null check (price_cents >= 0),
    description      text,
    featured         boolean not null default false,
    -- I servizi non si cancellano: si disattivano, così lo storico resta leggibile.
    active           boolean not null default true,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
create trigger services_touch before update on services
    for each row execute function touch_updated_at();
create index services_active_idx on services (active) where active;

create table operators (
    id          uuid primary key default gen_random_uuid(),
    name        text not null check (length(btrim(name)) > 0),
    title       text not null default 'Barbiere',
    bio         text not null default '',
    specialties text[] not null default '{}',
    is_owner    boolean not null default false,
    active      boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create trigger operators_touch before update on operators
    for each row execute function touch_updated_at();

create table operator_hours (
    operator_id uuid not null references operators (id) on delete cascade,
    day_of_week smallint not null check (day_of_week between 1 and 7),
    starts_at   time not null,
    ends_at     time not null,
    constraint operator_hours_range check (starts_at < ends_at),
    primary key (operator_id, day_of_week, starts_at)
);

create table operator_services (
    operator_id uuid not null references operators (id) on delete cascade,
    service_id  uuid not null references services (id) on delete cascade,
    primary key (operator_id, service_id)
);
create index operator_services_service_idx on operator_services (service_id);

-- ---------------------------------------------------------------- clienti ---

-- Scheda cliente del CRM. Esiste anche senza account: il titolare e gli
-- operatori creano schede per chi prenota al telefono o entra e basta.
create table clients (
    id                    uuid primary key default gen_random_uuid(),
    first_name            text not null check (length(btrim(first_name)) > 0),
    last_name             text not null check (length(btrim(last_name)) > 0),
    -- Numero in formato E.164 (+39…): la normalizzazione la fa l'API.
    phone                 text not null check (phone ~ '^\+[1-9][0-9]{7,14}$'),
    email                 citext,
    customer_since        date not null default current_date,
    marketing_opt_in      boolean not null default true,
    preferred_operator_id uuid references operators (id) on delete set null,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);
create trigger clients_touch before update on clients
    for each row execute function touch_updated_at();
create unique index clients_phone_key on clients (phone);
create unique index clients_email_key on clients (email) where email is not null;
create index clients_last_name_idx on clients (last_name, first_name);

create table client_preferred_services (
    client_id  uuid not null references clients (id) on delete cascade,
    service_id uuid not null references services (id) on delete cascade,
    primary key (client_id, service_id)
);

-- Account di accesso. Un CLIENT punta alla sua scheda CRM, uno STAFF/OWNER
-- al suo profilo operatore: il check impedisce le combinazioni impossibili.
create table users (
    id            uuid primary key default gen_random_uuid(),
    first_name    text not null check (length(btrim(first_name)) > 0),
    last_name     text not null check (length(btrim(last_name)) > 0),
    email         citext not null unique,
    phone         text not null check (phone ~ '^\+[1-9][0-9]{7,14}$'),
    role          user_role not null,
    password_hash text not null,
    member_since  date not null default current_date,
    avatar_url    text,
    client_id     uuid unique references clients (id) on delete restrict,
    operator_id   uuid unique references operators (id) on delete restrict,
    disabled_at   timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint users_role_link check (
        (role = 'CLIENT' and client_id is not null and operator_id is null)
        or (role in ('STAFF', 'OWNER') and operator_id is not null and client_id is null)
    )
);
create trigger users_touch before update on users
    for each row execute function touch_updated_at();

-- Refresh token a rotazione: si conserva solo l'hash, mai il token.
create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users (id) on delete cascade,
    token_hash  text not null unique,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    replaced_by uuid references refresh_tokens (id) on delete set null,
    user_agent  text,
    created_at  timestamptz not null default now()
);
create index refresh_tokens_user_idx on refresh_tokens (user_id) where revoked_at is null;

-- Reset password: token monouso, scadenza 30 minuti (come dice il mockup).
create table password_resets (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users (id) on delete cascade,
    token_hash text not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);

create table client_notification_prefs (
    user_id              uuid primary key references users (id) on delete cascade,
    appointment_reminder boolean not null default true,
    waitlist_alerts      boolean not null default true,
    marketing            boolean not null default false,
    updated_at           timestamptz not null default now()
);

create table device_tokens (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references users (id) on delete cascade,
    token        text not null unique,
    platform     device_platform not null,
    created_at   timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);
create index device_tokens_user_idx on device_tokens (user_id);

-- ----------------------------------------------------------------- agenda ---

create table appointments (
    id                uuid primary key default gen_random_uuid(),
    client_id         uuid not null references clients (id) on delete restrict,
    operator_id       uuid not null references operators (id) on delete restrict,
    starts_at         timestamptz not null,
    duration_minutes  integer not null check (duration_minutes > 0),
    -- Mantenuta dal trigger qui sotto: il database non accetta una colonna
    -- calcolata perché sommare minuti a un timestamptz dipende dal fuso.
    ends_at           timestamptz not null,
    -- Prezzo e durata sono fotografati alla prenotazione: ritoccare il listino
    -- non riscrive lo storico.
    total_price_cents bigint not null check (total_price_cents >= 0),
    status            appointment_status not null default 'CONFIRMED',
    channel           booking_channel not null default 'APP',
    note_for_operator text check (note_for_operator is null or length(note_for_operator) <= 200),
    cancelled_by      cancellation_actor,
    cancelled_at      timestamptz,
    completed_at      timestamptz,
    created_by_user_id uuid references users (id) on delete set null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint appointments_cancel_fields check (
        (status = 'CANCELLED') = (cancelled_at is not null)
        and (cancelled_at is null or cancelled_by is not null)
    ),
    constraint appointments_completed_fields check (
        (status = 'COMPLETED') = (completed_at is not null)
    ),
    -- Il cuore della concorrenza: due appuntamenti attivi non possono
    -- sovrapporsi sulla stessa poltrona, nemmeno se prenotati nello stesso
    -- istante da due dispositivi diversi.
    constraint appointments_no_overlap exclude using gist (
        operator_id with =,
        tstzrange(starts_at, ends_at, '[)') with &&
    ) where (status in ('CONFIRMED', 'IN_PROGRESS'))
);
-- ends_at segue sempre inizio + durata, chiunque scriva la riga.
create or replace function appointments_set_ends_at() returns trigger language plpgsql as $$
begin
    new.ends_at := new.starts_at + make_interval(mins => new.duration_minutes);
    return new;
end;
$$;
create trigger appointments_ends_at before insert or update on appointments
    for each row execute function appointments_set_ends_at();
create trigger appointments_touch before update on appointments
    for each row execute function touch_updated_at();
create index appointments_operator_start_idx on appointments (operator_id, starts_at);
create index appointments_client_start_idx on appointments (client_id, starts_at desc);
create index appointments_start_idx on appointments (starts_at);

-- Le righe del carrello, con nome, durata e prezzo del momento.
create table appointment_services (
    appointment_id   uuid not null references appointments (id) on delete cascade,
    position         smallint not null check (position >= 0),
    service_id       uuid not null references services (id) on delete restrict,
    name             text not null,
    duration_minutes integer not null check (duration_minutes > 0),
    price_cents      bigint not null check (price_cents >= 0),
    primary key (appointment_id, position)
);
create index appointment_services_service_idx on appointment_services (service_id);

-- Assenze di una giornata o parte di essa (permesso, pausa, corso).
create table time_blocks (
    id          uuid primary key default gen_random_uuid(),
    operator_id uuid not null references operators (id) on delete cascade,
    reason      block_reason not null,
    on_date     date not null,
    starts_at   time not null,
    ends_at     time not null,
    label       text,
    created_by_user_id uuid references users (id) on delete set null,
    created_at  timestamptz not null default now(),
    constraint time_blocks_range check (starts_at < ends_at)
);
create index time_blocks_operator_date_idx on time_blocks (operator_id, on_date);
create index time_blocks_date_idx on time_blocks (on_date);

-- Ferie: assenze su più giorni, estremi inclusi.
create table holidays (
    id          uuid primary key default gen_random_uuid(),
    operator_id uuid not null references operators (id) on delete cascade,
    from_date   date not null,
    to_date     date not null,
    label       text not null default 'Ferie',
    created_at  timestamptz not null default now(),
    constraint holidays_range check (from_date <= to_date),
    constraint holidays_no_overlap exclude using gist (
        operator_id with =,
        daterange(from_date, to_date, '[]') with &&
    )
);
create index holidays_operator_idx on holidays (operator_id, from_date);

-- Lista d'attesa. time null = "quel giorno, a qualsiasi ora";
-- operator_id null = "con chiunque". La posizione in coda è l'ordine di arrivo.
create table waitlist_entries (
    id                uuid primary key default gen_random_uuid(),
    client_id         uuid not null references clients (id) on delete cascade,
    on_date           date not null,
    at_time           time,
    operator_id       uuid references operators (id) on delete cascade,
    duration_minutes  integer not null check (duration_minutes > 0),
    total_price_cents bigint not null check (total_price_cents >= 0),
    status            waitlist_status not null default 'WAITING',
    notified_at       timestamptz,
    expires_at        timestamptz,
    created_at        timestamptz not null default now()
);
-- Una sola presenza in coda per cliente, giorno e operatore: niente doppie
-- posizioni per la stessa giornata (l'ora richiesta non la spezza).
create unique index waitlist_unique_waiting
    on waitlist_entries (client_id, on_date, coalesce(operator_id, '00000000-0000-0000-0000-000000000000'::uuid))
    where status = 'WAITING';
create index waitlist_queue_idx on waitlist_entries (on_date, created_at) where status = 'WAITING';

create table waitlist_entry_services (
    entry_id   uuid not null references waitlist_entries (id) on delete cascade,
    position   smallint not null check (position >= 0),
    service_id uuid not null references services (id) on delete cascade,
    primary key (entry_id, position)
);

-- ------------------------------------------------------------- notifiche ----

create table notifications (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users (id) on delete cascade,
    kind       notification_kind not null default 'GENERIC',
    title      text not null,
    body       text not null,
    -- Riferimenti per il tap (appointmentId, waitlistEntryId, …).
    payload    jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    read_at    timestamptz
);
create index notifications_user_idx on notifications (user_id, created_at desc);
create index notifications_unread_idx on notifications (user_id) where read_at is null;

-- Regole del salone: una riga sola.
create table notification_settings (
    id                  smallint primary key generated always as identity check (id = 1),
    booking_confirmation boolean not null default true,
    cancellation_alert   boolean not null default true,
    late_operator_alert  boolean not null default false,
    empty_day_promos     boolean not null default false,
    updated_at           timestamptz not null default now()
);

create table reminder_rules (
    id           uuid primary key default gen_random_uuid(),
    hours_before integer not null unique check (hours_before between 1 and 168),
    created_at   timestamptz not null default now()
);

create table push_campaigns (
    id              uuid primary key default gen_random_uuid(),
    name            text not null check (length(btrim(name)) > 0),
    segment         campaign_segment not null,
    title           text not null check (length(btrim(title)) > 0),
    body            text not null check (length(body) <= 140),
    scheduled_at    timestamptz,
    repeat_weekly   boolean not null default false,
    send_cap        integer check (send_cap is null or send_cap > 0),
    reachable_count integer not null default 0,
    segment_size    integer not null default 0,
    sent_count      integer not null default 0,
    status          campaign_status not null default 'DRAFT',
    last_sent_at    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint push_campaigns_scheduled check (status <> 'SCHEDULED' or scheduled_at is not null)
);
create trigger push_campaigns_touch before update on push_campaigns
    for each row execute function touch_updated_at();

create table campaign_sends (
    id          uuid primary key default gen_random_uuid(),
    campaign_id uuid not null references push_campaigns (id) on delete cascade,
    client_id   uuid not null references clients (id) on delete cascade,
    user_id     uuid references users (id) on delete set null,
    sent_at     timestamptz not null default now(),
    delivered   boolean not null default false,
    error       text
);
create index campaign_sends_campaign_idx on campaign_sends (campaign_id);

-- ------------------------------------------------------------------ viste ---

-- I contatori del CRM sono calcolati, non salvati: così "segna completato"
-- può essere richiamato senza contare due volte, e un annullamento corregge
-- i numeri da solo.
create view client_stats as
select
    c.id                                                                as client_id,
    count(*) filter (where a.status = 'COMPLETED')::int                 as visit_count,
    coalesce(sum(a.total_price_cents) filter (where a.status = 'COMPLETED'), 0)::bigint
                                                                        as lifetime_spend_cents,
    count(*) filter (where a.status = 'NO_SHOW')::int                   as no_show_count,
    max(a.starts_at) filter (where a.status = 'COMPLETED')              as last_visit_at
from clients c
left join appointments a on a.client_id = c.id
group by c.id;
