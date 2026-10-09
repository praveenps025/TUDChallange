-- Equipment registration: each type has a label prefix; new items get the next number (WC-032)
alter table equipment_type add column label_prefix varchar(10);
update equipment_type set label_prefix = case name
  when 'Wheelchair' then 'WC' when 'Pressure cushion' then 'CU' when 'Sliding board' then 'SB' end;
update equipment_type set label_prefix = upper(left(regexp_replace(name, '[^A-Za-z]', '', 'g'), 3)) || id
  where label_prefix is null;
alter table equipment_type alter column label_prefix set not null;
alter table equipment_type add constraint uq_equipment_type_prefix unique (label_prefix);
alter table equipment_type add constraint ck_equipment_type_prefix check (label_prefix ~ '^[A-Z0-9]{1,10}$');

-- When the QR label was last printed (registration or reprint)
alter table equipment add column label_printed_at timestamptz;

-- Waitlist priority, chosen by whoever makes the request; the queue is sorted by it
alter table waitlist_request add column priority varchar(10) not null default 'routine'
  check (priority in ('urgent', 'soon', 'routine'));
alter table waitlist_request add column note varchar(200);

-- A manager's approval or rejection always carries a reason
alter table discharge_override add column decision_reason varchar(500);
