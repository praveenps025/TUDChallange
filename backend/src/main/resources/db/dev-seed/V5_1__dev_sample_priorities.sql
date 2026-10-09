-- Development sample data: give the sample waitlist a mix of priorities
update waitlist_request w set priority = v.priority
from (values ('Ward 2C', 'urgent'), ('Ward 5B', 'soon'), ('Ward 4A', 'soon')) as v(ward, priority)
join location l on l.name = v.ward
where w.ward_id = l.id;
