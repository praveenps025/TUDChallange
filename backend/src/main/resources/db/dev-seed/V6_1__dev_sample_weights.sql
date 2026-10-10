-- Development sample data: weight ratings for the sample equipment
update equipment e set weight_category_id = w.id
from (values
  ('CU-024', 'Standard'), ('CU-009', 'Standard'), ('CU-031', 'Standard'),
  ('WC-018', 'Standard'), ('WC-026', 'Standard'), ('WC-031', 'Overweight'),
  ('SB-011', 'Bariatric'), ('SB-006', 'Standard')
) as v(code, band)
join weight_category w on w.name = v.band
where e.label_code = v.code;
