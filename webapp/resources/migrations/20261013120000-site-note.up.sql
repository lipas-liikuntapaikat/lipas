-- Per-site notes ("muistilaput") that an org's members leave on the sites the
-- org owns. Visible to that org only (and LIPAS admins) — deliberately NOT part
-- of the sports_site document, which is public (search, API, exports).
--
-- Append-only: a note's text never changes once written, and its lifecycle
-- (active -> archived, archived -> active) is a log of status rows.
-- The current status is the latest site_note_status row; no row = active.
CREATE TABLE IF NOT EXISTS public.site_note (
  id          uuid NOT NULL DEFAULT uuid_generate_v4(),
  lipas_id    integer NOT NULL,
  -- No FK on org_id: the org revision table has many rows per org_id, so it
  -- cannot be a FK target (same as org_takeover_request).
  org_id      uuid NOT NULL,
  body        text NOT NULL,
  author_id   uuid NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT site_note_pkey PRIMARY KEY (id),
  CONSTRAINT site_note_author_fk FOREIGN KEY (author_id)
    REFERENCES public.account (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
);

--;;

ALTER TABLE public.site_note OWNER TO lipas;

--;;

CREATE INDEX site_note_lipas_id_idx ON public.site_note (lipas_id);

--;;

CREATE INDEX site_note_org_id_idx ON public.site_note (org_id);

--;;

CREATE TABLE IF NOT EXISTS public.site_note_status (
  id          uuid NOT NULL DEFAULT uuid_generate_v4(),
  note_id     uuid NOT NULL,
  status      text NOT NULL,
  author_id   uuid NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT site_note_status_pkey PRIMARY KEY (id),
  CONSTRAINT site_note_status_status_check
    CHECK (status IN ('active', 'archived')),
  CONSTRAINT site_note_status_note_fk FOREIGN KEY (note_id)
    REFERENCES public.site_note (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION,
  CONSTRAINT site_note_status_author_fk FOREIGN KEY (author_id)
    REFERENCES public.account (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
);

--;;

ALTER TABLE public.site_note_status OWNER TO lipas;

--;;

CREATE INDEX site_note_status_note_id_idx
  ON public.site_note_status (note_id, created_at DESC);

--;;

-- Enforce the append-only contract in the database, not just in the app.
CREATE OR REPLACE FUNCTION public.site_note_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'site notes are append-only: % on % is not allowed',
    TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

--;;

CREATE TRIGGER site_note_append_only
  BEFORE UPDATE OR DELETE ON public.site_note
  FOR EACH ROW EXECUTE FUNCTION public.site_note_append_only();

--;;

CREATE TRIGGER site_note_status_append_only
  BEFORE UPDATE OR DELETE ON public.site_note_status
  FOR EACH ROW EXECUTE FUNCTION public.site_note_append_only();

--;;

-- One row per note with its current status (latest status row, else active).
CREATE OR REPLACE VIEW public.site_note_current AS
SELECT n.id,
       n.lipas_id,
       n.org_id,
       n.body,
       n.author_id,
       n.created_at,
       COALESCE(s.status, 'active') AS status,
       s.author_id  AS status_author_id,
       s.created_at AS status_changed_at
FROM public.site_note n
LEFT JOIN LATERAL (
  SELECT ss.status, ss.author_id, ss.created_at
  FROM public.site_note_status ss
  WHERE ss.note_id = n.id
  ORDER BY ss.created_at DESC
  LIMIT 1
) s ON true;

--;;

ALTER VIEW public.site_note_current OWNER TO lipas;
