-- Only one audit walk can be in progress at a time, even if two people start one together
create unique index ux_audit_walk_one_open on audit_walk ((true)) where status = 'open';
