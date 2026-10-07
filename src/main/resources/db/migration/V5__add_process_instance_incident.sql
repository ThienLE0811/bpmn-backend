-- A service task connector runs partway through a walk of an instance that already exists, so a
-- failure there cannot simply abort the request: the instance is parked in FAILED with the node
-- and reason recorded, instead of the walk being lost.
ALTER TABLE public.process_instances
    ADD COLUMN IF NOT EXISTS incident_node_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS incident_message TEXT;
