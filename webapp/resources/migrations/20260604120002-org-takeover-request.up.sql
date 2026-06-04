-- Workflow state for an org claiming ownership of the sites matching its
-- ownership rule. Distinct from membership (which lives in the org document):
-- take-over crosses org boundaries and wants LIPAS-admin approval, so it is a
-- small requested -> approved/denied state machine. The actual ownership
-- application is append-only site revisions (acting_org_id), done on approval.
CREATE TABLE IF NOT EXISTS public.org_takeover_request (
  id             uuid NOT NULL DEFAULT uuid_generate_v4(),
  org_id         uuid NOT NULL,
  status         text NOT NULL DEFAULT 'requested',  -- requested | approved | denied
  ownership_rule jsonb,                               -- {:city-codes [..] :owners [..]} snapshot
  lipas_ids      jsonb,                               -- matched lipas-ids at request time (transparency)
  requested_by   uuid,
  requested_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_by     uuid,
  decided_at     timestamptz,

  -- No FK on org_id: the org revision table has many rows per org_id (it is not
  -- unique), so org_id cannot be a FK target. Validated at the application layer.
  CONSTRAINT org_takeover_request_pkey PRIMARY KEY (id)
);

--;;

ALTER TABLE public.org_takeover_request OWNER TO lipas;

--;;

CREATE INDEX org_takeover_request_org_id_idx ON public.org_takeover_request (org_id);

--;;

CREATE INDEX org_takeover_request_status_idx ON public.org_takeover_request (status);
