-- Recreate legacy integration tables (without data)

CREATE TABLE integration_out_queue (
    lipas_id INTEGER NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE integration_log (
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_date TIMESTAMPTZ NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL,
    document JSONB
);
