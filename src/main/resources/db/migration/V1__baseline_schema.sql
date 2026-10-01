-- Baseline snapshot of the schema as it existed before Flyway was introduced.
-- Matches the final shape produced by the old DatabaseConfig.initDatabase() CREATE TABLE /
-- ALTER TABLE ADD COLUMN IF NOT EXISTS statements. On environments that already have these
-- tables, Flyway records this version as the baseline without executing it (see
-- DatabaseConfig.initDatabase, baselineOnMigrate/baselineVersion). On a brand new database,
-- this script creates the full schema from scratch.

CREATE TABLE IF NOT EXISTS public.users (
    id VARCHAR(100) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50),
    status VARCHAR(50),
    password_hash VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.bpmn_processes (
    id VARCHAR(100) PRIMARY KEY,
    process_key VARCHAR(100) NOT NULL,
    process_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    version INT DEFAULT 1,
    bpmn_xml TEXT,
    status VARCHAR(50),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.dmn_decision (
    id VARCHAR(100) PRIMARY KEY,
    decision_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    hit_policy VARCHAR(50),
    category VARCHAR(100),
    version INT DEFAULT 1,
    dmn_xml TEXT,
    status VARCHAR(50),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.workflows (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.process_instances (
    id VARCHAR(100) PRIMARY KEY,
    process_id VARCHAR(100) NOT NULL REFERENCES public.bpmn_processes(id),
    process_version INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_node_id VARCHAR(100),
    variables TEXT,
    pending_join_arrivals TEXT,
    started_by VARCHAR(100),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.tasks (
    id VARCHAR(100) PRIMARY KEY,
    process_id VARCHAR(100),
    process_instance_id VARCHAR(100) REFERENCES public.process_instances(id) ON DELETE CASCADE,
    node_id VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    assignee_id VARCHAR(100),
    status VARCHAR(50),
    claimed_by VARCHAR(100),
    claimed_at TIMESTAMP,
    completed_by VARCHAR(100),
    completed_at TIMESTAMP,
    due_date TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.bpmn_process_versions (
    id VARCHAR(100) PRIMARY KEY,
    process_id VARCHAR(100) NOT NULL REFERENCES public.bpmn_processes(id) ON DELETE CASCADE,
    version INT NOT NULL,
    bpmn_xml TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP,
    UNIQUE (process_id, version)
);

CREATE TABLE IF NOT EXISTS public.dmn_decision_versions (
    id VARCHAR(100) PRIMARY KEY,
    decision_id VARCHAR(100) NOT NULL REFERENCES public.dmn_decision(id) ON DELETE CASCADE,
    version INT NOT NULL,
    dmn_xml TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP,
    UNIQUE (decision_id, version)
);

CREATE TABLE IF NOT EXISTS public.refresh_tokens (
    id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
