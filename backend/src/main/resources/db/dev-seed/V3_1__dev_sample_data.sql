-- Development sample data (dev profile only): the same equipment, waitlist,
-- activity, patient and audit walk as src/mockData.js, so every screen looks
-- the same against Postgres. Dates are relative to when the seed first runs.
-- The patient and MRN are fictional. "Today" is the hospital's date
-- (${app_timezone}), not the database session's.

create temporary table seed_today as select (now() at time zone '${app_timezone}')::date as today;

-- ------------------------------------------------------------- equipment

insert into equipment (label_code, name, type_id, pool_id, status, location_id, last_seen_at, status_changed_at)
select v.code, v.name, t.id, p.id, v.status, l.id, now() - v.seen, now() - v.changed
from (values
  ('CU-024', 'Pressure cushion',   'Pressure cushion', 'issued',    'Ward 2B',  interval '12 days', interval '12 days'),
  ('CU-009', 'Pressure cushion',   'Pressure cushion', 'available', 'OT store', interval '1 hour',  interval '1 hour'),
  ('CU-031', 'Pressure cushion',   'Pressure cushion', 'missing',   'Ward 3B',  interval '11 days', interval '2 days'),
  ('WC-018', 'Transit wheelchair', 'Wheelchair',       'missing',   'Ward 4C',  interval '10 days', interval '2 days'),
  ('WC-026', 'Wheelchair',         'Wheelchair',       'issued',    'Ward 5A',  interval '1 hour',  interval '1 hour'),
  ('WC-031', 'Wheelchair',         'Wheelchair',       'available', 'OT store', interval '4 days',  interval '4 days'),
  ('SB-011', 'Sliding board',      'Sliding board',    'inspect',   'OT store', interval '40 minutes', interval '40 minutes'),
  ('SB-006', 'Sliding board',      'Sliding board',    'missing',   'OT store', interval '9 days',  interval '2 days')
) as v(code, name, type_name, status, location_name, seen, changed)
join equipment_type t on t.name = v.type_name
join equipment_pool p on p.code = 'MAIN'
join location l on l.name = v.location_name;

-- --------------------------------------------------------------- patient

insert into patient (mrn_hash, mrn_suffix, ward_id, bed, discharge_planned_on)
-- The real MRN hash needs the secret key, so the app fills it in on startup (DevUserSeeder)
select rpad('sample-patient-4821', 64, '-'), '4821', l.id, '06', (select today from seed_today)
from location l where l.name = 'Ward 4C';

-- ----------------------------------------------------------------- loans

-- CU-024 went out 12 days ago and is due today; WC-026 went out today;
-- WC-018 is still on loan to the patient in Ward 4C but has gone missing.
insert into loan (equipment_id, patient_id, ward_id, issued_at, due_at)
select e.id, p.id, l.id, now() - v.issued, ((select today from seed_today) + v.due_in_days + time '23:59:59') at time zone '${app_timezone}'
from (values
  ('CU-024', null,   'Ward 2B', interval '12 days', 0),
  ('WC-026', null,   'Ward 5A', interval '1 hour',  0),
  ('WC-018', '4821', 'Ward 4C', interval '14 days', 7)
) as v(code, mrn_suffix, ward, issued, due_in_days)
join equipment e on e.label_code = v.code
join location l on l.name = v.ward
left join patient p on p.mrn_suffix = v.mrn_suffix;

-- -------------------------------------------------------------- waitlist

insert into waitlist_request (ward_id, type_id, requested_at)
select l.id, t.id, now() - v.age
from (values
  ('Ward 3A', 'Pressure cushion', interval '6 days'),
  ('Ward 5B', 'Wheelchair',       interval '5 days'),
  ('Ward 2C', 'Sliding board',    interval '4 days'),
  ('Ward 4A', 'Pressure cushion', interval '3 days'),
  ('Ward 1B', 'Wheelchair',       interval '2 days'),
  ('Ward 6A', 'Pressure cushion', interval '1 day 1 hour'),
  ('Ward 2A', 'Wheelchair',       interval '1 day')
) as v(ward, type_name, age)
join location l on l.name = v.ward
join equipment_type t on t.name = v.type_name;

-- -------------------------------------------------------------- activity

insert into activity_log (kind, title, detail, equipment_id, occurred_at)
select v.kind, v.title, v.detail, e.id, now() - v.ago
from (values
  ('in',        'Sliding board returned', 'SB-011 · OT store',  'SB-011', interval '40 minutes'),
  ('out',       'Wheelchair issued',      'WC-026 · Ward 5A',   'WC-026', interval '1 hour'),
  ('clipboard', 'Cushion inspected',      'CU-009 · Available', 'CU-009', interval '1 hour 20 minutes')
) as v(kind, title, detail, code, ago)
join equipment e on e.label_code = v.code;

-- ------------------------------------------------------------ audit walk

-- A walk in progress round Ward 3 and the OT store: five items expected
-- there, three scanned so far.
insert into audit_walk (area, started_at) values ('Ward 3 · OT store', now() - interval '30 minutes');

insert into audit_walk_location (audit_walk_id, location_id)
select w.id, l.id from audit_walk w, location l
where w.area = 'Ward 3 · OT store' and l.name in ('Ward 3A', 'Ward 3B', 'OT store');

insert into audit_walk_item (audit_walk_id, equipment_id, expected_location_id)
select w.id, e.id, e.location_id from audit_walk w
join audit_walk_location wl on wl.audit_walk_id = w.id
join equipment e on e.location_id = wl.location_id
where w.area = 'Ward 3 · OT store';

insert into audit_scan (audit_walk_id, equipment_id, scanned_at)
select w.id, e.id, now() - interval '20 minutes' from audit_walk w, equipment e
where w.area = 'Ward 3 · OT store' and e.label_code in ('CU-009', 'WC-031', 'SB-011');
