-- When the item last changed status; drives notes such as "Returned today · Needs inspection"
alter table equipment add column status_changed_at timestamptz not null default now();
