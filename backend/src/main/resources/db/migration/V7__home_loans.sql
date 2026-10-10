-- Home loans: equipment a patient takes home, approved by a manager.
-- The loan itself stays in the loan table; home_since marks it as a home loan.

insert into location (name, kind) values ('Home loan', 'other');

alter table loan add column home_since timestamptz;
alter table loan add column home_note varchar(200);

-- A request (from the discharge check or an item's page) waiting for a manager
create table home_loan_request (
  id              bigint generated always as identity primary key,
  patient_id      bigint       not null references patient,
  status          varchar(10)  not null default 'requested' check (status in ('requested', 'approved', 'rejected')),
  note            varchar(200),
  requested_by    bigint       references app_user,
  requested_at    timestamptz  not null default now(),
  decided_by      bigint       references app_user,
  decided_at      timestamptz,
  decision_reason varchar(500),
  created_at      timestamptz  not null default now(),
  updated_at      timestamptz  not null default now()
);
create unique index ux_home_loan_pending on home_loan_request (patient_id) where status = 'requested';
create index ix_home_loan_status on home_loan_request (status, requested_at);

create table home_loan_request_item (
  request_id   bigint      not null references home_loan_request on delete cascade,
  equipment_id bigint      not null references equipment,
  due_on       date        not null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  primary key (request_id, equipment_id)
);

create trigger trg_home_loan_request_updated before update on home_loan_request
  for each row execute function set_updated_at();
create trigger trg_home_loan_request_item_updated before update on home_loan_request_item
  for each row execute function set_updated_at();
