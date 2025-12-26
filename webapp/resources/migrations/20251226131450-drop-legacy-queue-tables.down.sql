-- Recreate legacy queue tables (without data)

CREATE TABLE analysis_queue (
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'pending',
    lipas_id INTEGER NOT NULL
);

CREATE TABLE analysis_queue_backup (
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'pending',
    lipas_id INTEGER NOT NULL
);

CREATE TABLE elevation_queue (
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'pending',
    lipas_id INTEGER NOT NULL
);

CREATE TABLE elevation_queue_backup (
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'pending',
    lipas_id INTEGER NOT NULL
);

CREATE TABLE email_out_queue (
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message JSONB NOT NULL,
    id UUID NOT NULL DEFAULT uuid_generate_v4()
);

CREATE TABLE email_out_queue_backup (
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message JSONB NOT NULL,
    id UUID NOT NULL DEFAULT uuid_generate_v4()
);

CREATE TABLE webhook_queue (
    id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'pending',
    batch_data JSONB NOT NULL DEFAULT '{}'
);
