-- The `workflows` table backed an early first-iteration "Workflow" CRUD feature that was
-- superseded by the real BPMN engine (bpmn_processes / process_instances / tasks). It has
-- no foreign keys in or out, and neither the backend engine nor the frontend ever read/wrote
-- it in practice (confirmed via a full codebase audit). Dropping it now that the Workflow
-- model/service/controller/repository have been removed from the codebase.
DROP TABLE IF EXISTS public.workflows;
