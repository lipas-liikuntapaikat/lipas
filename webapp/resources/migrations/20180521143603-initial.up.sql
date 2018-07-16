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
