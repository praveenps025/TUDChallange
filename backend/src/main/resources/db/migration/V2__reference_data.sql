-- Reference data used by every environment. Add new categories, types,
-- pools and locations with a new migration (or later, an admin screen);
-- nothing here is hardcoded in Java.

insert into equipment_category (name, icon_key, sort_order) values
  ('Wheelchairs',    'wheelchair', 1),
  ('Cushions',       'layers',     2),
  ('Sliding boards', 'layers',     3);

-- default_loan_days is a starting point; staff can set a different due date when issuing
insert into equipment_type (name, category_id, default_loan_days, sort_order) values
  ('Wheelchair',       (select id from equipment_category where name = 'Wheelchairs'),    14, 1),
  ('Pressure cushion', (select id from equipment_category where name = 'Cushions'),       14, 2),
  ('Sliding board',    (select id from equipment_category where name = 'Sliding boards'), 14, 3);

insert into equipment_pool (code, name, description, sort_order) values
  ('MAIN',    'Main OT stock',         'General OT equipment store',          1),
  ('BURKITT', 'Burkitt / ARHC',        'Held for Burkitt and ARHC',           2),
  ('WSU_ASU', 'WSU / ASU own funding', 'Bought from WSU / ASU budgets',       3),
  ('WINTER',  'Winter Initiative',     'Bought through the Winter Initiative', 4);

insert into location (name, kind) values
  ('OT store', 'store'),
  ('Workshop', 'workshop'),
  ('Ward 1B', 'ward'), ('Ward 2A', 'ward'), ('Ward 2B', 'ward'), ('Ward 2C', 'ward'),
  ('Ward 3A', 'ward'), ('Ward 3B', 'ward'), ('Ward 4A', 'ward'), ('Ward 4C', 'ward'),
  ('Ward 5A', 'ward'), ('Ward 5B', 'ward'), ('Ward 6A', 'ward');
