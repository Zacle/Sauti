-- Minutes and outbound messages a platform admin adds to a workspace's allowance, for example for a
-- pilot: added to the one-off trial allowance, or to every monthly cycle of a paid plan. Usage is no
-- longer kept as a running counter; minutes are summed from the calls of the current cycle and
-- messages from the billing ledger.
ALTER TABLE tenants ADD COLUMN extra_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE tenants ADD COLUMN extra_messages INT NOT NULL DEFAULT 0;
