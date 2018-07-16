CREATE TABLE public.account (
  id            uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at    timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
  email         text COLLATE pg_catalog."default" NOT NULL,
  username      text COLLATE pg_catalog."default" NOT NULL,
  password      text COLLATE pg_catalog."default" NOT NULL,
  permissions   jsonb,
  user_data     jsonb,
  refresh_token text COLLATE pg_catalog."default",

  CONSTRAINT account_pkey PRIMARY KEY (id),
  CONSTRAINT account_email_key UNIQUE (email),
  CONSTRAINT account_username_key UNIQUE (username)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.account
OWNER to lipas;

-- Table: public.sports_site

-- DROP TABLE public.sports_site;

CREATE TABLE public.sports_site (
created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
lipas_id   integer NOT NULL,
status     text COLLATE pg_catalog."default" NOT NULL,
document   jsonb NOT NULL,
author_id  uuid NOT NULL,
type_code  integer NOT NULL,
city_code  text COLLATE pg_catalog."default" NOT NULL,

CONSTRAINT "sports-site_pkey" PRIMARY KEY (created_at, lipas_id),
CONSTRAINT author_id_account_fk FOREIGN KEY (author_id)
REFERENCES public.account (id) MATCH SIMPLE
ON UPDATE NO ACTION
ON DELETE NO ACTION
) WITH (
OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.sports_site
OWNER to lipas;
