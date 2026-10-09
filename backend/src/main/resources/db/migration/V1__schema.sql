-- EquipTrack OT schema. Flyway owns this; Hibernate only validates it.
-- Every table has created_at / updated_at; updated_at is kept current by a trigger.

create function set_updated_at() returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

-- ---------------------------------------------------------------- users

create table app_user (
  id              bigint generated always as identity primary key,
  username        varchar(60)  not null,
  password_hash   varchar(100) not null,
  display_name    varchar(100) not null,
  team            varchar(100) not null,
  role            varchar(20)  not null check (role in ('MANAGER', 'OT_STAFF')),
  active          boolean      not null default true,
  failed_attempts integer      not null default 0,
  locked_until    timestamptz,
  last_login_at   timestamptz,
  version         bigint       not null default 0,
  created_at      timestamptz  not null default now(),
  updated_at      timestamptz  not null default now()
);
create unique index ux_app_user_username on app_user (lower(username));

-- ------------------------------------------------------- reference data

create table equipment_category (
  id         bigint generated always as identity primary key,
  name       varchar(60) not null unique,   -- plural, e.g. Wheelchairs
  icon_key   varchar(30) not null,          -- name in src/components/Icon.jsx
  sort_order integer     not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table equipment_type (
  id                bigint generated always as identity primary key,
  name              varchar(60) not null unique,  -- equals waitlist "type", e.g. Wheelchair
  category_id       bigint      not null references equipment_category,
  default_loan_days integer     not null default 14 check (default_loan_days > 0),
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);
create index ix_equipment_type_category on equipment_type (category_id);

-- Funding source or site that owns the item (tracker sheet groups)
create table equipment_pool (
  id          bigint generated always as identity primary key,
  code        varchar(30)  not null unique,
  name        varchar(100) not null,
  description varchar(300),
  sort_order  integer      not null default 0,
  created_at  timestamptz  not null default now(),
  updated_at  timestamptz  not null default now()
);

create table location (
  id         bigint generated always as identity primary key,
  name       varchar(60) not null unique,
  kind       varchar(20) not null check (kind in ('ward', 'store', 'workshop', 'other')),
  active     boolean     not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- ------------------------------------------------------------ equipment

create table equipment (
  id                bigint generated always as identity primary key,
  label_code        varchar(30)  not null check (label_code = upper(label_code)), -- the scanned id
  name              varchar(100) not null,
  type_id           bigint       not null references equipment_type,
  pool_id           bigint       not null references equipment_pool,
  status            varchar(10)  not null check (status in ('available', 'issued', 'inspect', 'missing')),
  location_id       bigint       not null references location,
  note              varchar(200),
  last_seen_at      timestamptz,

  -- Columns carried over from the Excel tracker
  asset_flag        varchar(10),            -- AT+ / AT-
  asset_number      varchar(50),
  model             varchar(100),
  size              varchar(50),            -- width x depth, as written on the tracker
  serial_number     varchar(100),
  hc21_number       varchar(50),
  dml_number        varchar(50),
  max_user_weight   varchar(30),
  therapist         varchar(100),
  tracker_status    varchar(100),
  tracker_updated_on date,
  comments          text,
  last_service_date date,
  purchase_date     date,

  version           bigint       not null default 0,
  created_at        timestamptz  not null default now(),
  updated_at        timestamptz  not null default now()
);
create unique index ux_equipment_label_code on equipment (label_code);
create index ix_equipment_status   on equipment (status);
create index ix_equipment_type     on equipment (type_id);
create index ix_equipment_pool     on equipment (pool_id);
create index ix_equipment_location on equipment (location_id);
create index ix_equipment_last_seen on equipment (last_seen_at);

create table service_record (
  id           bigint generated always as identity primary key,
  equipment_id bigint      not null references equipment,
  service_date date        not null,
  notes        text,
  recorded_by  bigint      references app_user,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create index ix_service_record_equipment on service_record (equipment_id, service_date desc);

-- -------------------------------------------------------------- patients

-- Only the minimum is stored: an HMAC of the full MRN (so each patient is
-- unique) and the last four digits used for lookup and display.
create table patient (
  id                   bigint generated always as identity primary key,
  mrn_hash             char(64)    not null unique,
  mrn_suffix           char(4)     not null check (mrn_suffix ~ '^[0-9]{4}$'),
  ward_id              bigint      references location,
  bed                  varchar(10),
  discharge_planned_on date,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);
create index ix_patient_mrn_suffix on patient (mrn_suffix);

-- --------------------------------------------------------------- waitlist

create table waitlist_request (
  id                     bigint generated always as identity primary key,
  ward_id                bigint      not null references location,
  type_id                bigint      not null references equipment_type,
  patient_id             bigint      references patient,
  status                 varchar(10) not null default 'open' check (status in ('open', 'allocated', 'cancelled')),
  requested_by           bigint      references app_user,
  requested_at           timestamptz not null default now(),
  allocated_equipment_id bigint      references equipment,
  closed_by              bigint      references app_user,
  closed_at              timestamptz,
  version                bigint      not null default 0,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now()
);
create index ix_waitlist_open on waitlist_request (type_id, requested_at) where status = 'open';

-- ------------------------------------------------------------------ loans

create table loan (
  id                  bigint generated always as identity primary key,
  equipment_id        bigint      not null references equipment,
  patient_id          bigint      references patient,
  ward_id             bigint      not null references location,
  waitlist_request_id bigint      references waitlist_request,
  issued_by           bigint      references app_user,
  issued_at           timestamptz not null default now(),
  due_at              timestamptz not null,
  returned_at         timestamptz,
  returned_by         bigint      references app_user,
  returned_clean      boolean,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  check ((returned_at is null) = (returned_clean is null))
);
-- An item can only be on one open loan at a time
create unique index ux_loan_open_equipment on loan (equipment_id) where returned_at is null;
create index ix_loan_patient_open on loan (patient_id) where returned_at is null;
create index ix_loan_due_open on loan (due_at) where returned_at is null;
create index ix_loan_returned_at on loan (returned_at);

-- ---------------------------------------------------------------- activity

create table activity_log (
  id           bigint generated always as identity primary key,
  kind         varchar(10)  not null check (kind in ('in', 'out', 'clipboard')),
  title        varchar(100) not null,
  detail       varchar(200) not null,
  user_id      bigint       references app_user,
  equipment_id bigint       references equipment,
  occurred_at  timestamptz  not null default now(),
  created_at   timestamptz  not null default now(),
  updated_at   timestamptz  not null default now()
);
create index ix_activity_occurred on activity_log (occurred_at desc);
create index ix_activity_user on activity_log (user_id, occurred_at desc);

-- ------------------------------------------------------------------ audits

create table audit_walk (
  id         bigint generated always as identity primary key,
  area       varchar(100) not null,           -- display text, e.g. "Ward 3 · OT store"
  status     varchar(10)  not null default 'open' check (status in ('open', 'closed')),
  started_by bigint       references app_user,
  started_at timestamptz  not null default now(),
  closed_by  bigint       references app_user,
  closed_at  timestamptz,
  version    bigint       not null default 0,
  created_at timestamptz  not null default now(),
  updated_at timestamptz  not null default now()
);
create index ix_audit_walk_open on audit_walk (started_at desc) where status = 'open';

create table audit_walk_location (
  audit_walk_id bigint not null references audit_walk on delete cascade,
  location_id   bigint      not null references location,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  primary key (audit_walk_id, location_id)
);

-- Items expected in the walk's area, captured when the walk starts
create table audit_walk_item (
  audit_walk_id        bigint      not null references audit_walk on delete cascade,
  equipment_id         bigint      not null references equipment,
  expected_location_id bigint      not null references location,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  primary key (audit_walk_id, equipment_id)
);

create table audit_scan (
  id            bigint generated always as identity primary key,
  audit_walk_id bigint      not null references audit_walk on delete cascade,
  equipment_id  bigint      not null references equipment,
  scanned_by    bigint      references app_user,
  scanned_at    timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (audit_walk_id, equipment_id)
);

-- --------------------------------------------------------------- discharge

create table discharge_override (
  id           bigint generated always as identity primary key,
  patient_id   bigint       not null references patient,
  reason       varchar(500) not null,
  status       varchar(10)  not null default 'requested' check (status in ('requested', 'approved', 'rejected')),
  requested_by bigint       references app_user,
  requested_at timestamptz  not null default now(),
  decided_by   bigint       references app_user,
  decided_at   timestamptz,
  created_at   timestamptz  not null default now(),
  updated_at   timestamptz  not null default now()
);
create unique index ux_override_pending on discharge_override (patient_id) where status = 'requested';

-- ------------------------------------------------------- updated_at triggers

do $$
declare t text;
begin
  foreach t in array array[
    'app_user', 'equipment_category', 'equipment_type', 'equipment_pool', 'location',
    'equipment', 'service_record', 'patient', 'waitlist_request', 'loan', 'activity_log',
    'audit_walk', 'audit_walk_location', 'audit_walk_item', 'audit_scan', 'discharge_override'
  ] loop
    execute format('create trigger trg_%1$s_updated before update on %1$I
                    for each row execute function set_updated_at()', t);
  end loop;
end $$;
