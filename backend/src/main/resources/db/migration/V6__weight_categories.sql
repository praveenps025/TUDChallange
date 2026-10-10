-- Patient weight bands an item is rated for. An item rated for a band also
-- suits patients in the lighter bands. Edit names and limits here (or in a
-- later admin screen); nothing in Java depends on them.
create table weight_category (
  id         bigint generated always as identity primary key,
  name       varchar(40) not null unique,
  max_kg     integer     not null unique check (max_kg > 0),
  sort_order integer     not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create trigger trg_weight_category_updated before update on weight_category
  for each row execute function set_updated_at();

insert into weight_category (name, max_kg, sort_order) values
  ('Underweight', 60, 1),
  ('Standard', 125, 2),
  ('Overweight', 170, 3),
  ('Bariatric', 250, 4);

alter table equipment add column weight_category_id bigint references weight_category;
create index ix_equipment_weight_category on equipment (weight_category_id);
