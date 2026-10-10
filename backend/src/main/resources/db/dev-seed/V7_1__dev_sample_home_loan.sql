-- Development sample data: a sliding board on loan to the sample patient on
-- Ward 4C, so "Send home on loan" can be tried at the discharge check
insert into equipment (label_code, name, type_id, pool_id, status, location_id, last_seen_at, status_changed_at, weight_category_id)
select 'SB-014', 'Sliding board', t.id, p.id, 'issued', l.id, now() - interval '3 days', now() - interval '3 days', w.id
from equipment_type t, equipment_pool p, location l, weight_category w
where t.name = 'Sliding board' and p.code = 'MAIN' and l.name = 'Ward 4C' and w.name = 'Standard';

insert into loan (equipment_id, patient_id, ward_id, issued_at, due_at)
select e.id, pt.id, l.id, now() - interval '3 days',
       (((now() at time zone '${app_timezone}')::date + 4) + time '23:59:59') at time zone '${app_timezone}'
from equipment e, patient pt, location l
where e.label_code = 'SB-014' and pt.mrn_suffix = '4821' and l.name = 'Ward 4C';
