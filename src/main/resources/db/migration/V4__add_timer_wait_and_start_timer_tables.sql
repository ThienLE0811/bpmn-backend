CREATE TABLE IF NOT EXISTS public.process_instance_timers (
    id VARCHAR(100) PRIMARY KEY,
    process_instance_id VARCHAR(100) NOT NULL REFERENCES public.process_instances(id) ON DELETE CASCADE,
    node_id VARCHAR(100) NOT NULL,
    due_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS public.bpmn_process_start_timers (
    process_id VARCHAR(100) PRIMARY KEY REFERENCES public.bpmn_processes(id) ON DELETE CASCADE,
    next_fire_at TIMESTAMP NOT NULL,
    repeats_remaining INT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
