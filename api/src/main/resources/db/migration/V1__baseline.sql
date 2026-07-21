-- V1 baseline: the CURRENT website schema (Drizzle: website/src/lib/todo/schema.ts).
--
-- On the real Neon database these tables already exist and Flyway is configured
-- with baselineOnMigrate=true / baselineVersion=1, so this script is NEVER run
-- there: Flyway simply records version 1 as the baseline and moves on to V2.
-- On a fresh, EMPTY database (for example the embedded Postgres used in tests)
-- there is nothing to baseline, so Flyway runs this script to create the schema
-- from scratch, then V2. Keep this in lockstep with the Drizzle schema.

CREATE TYPE todo_status AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'DELAYED', 'NEED_HELP', 'DONE');

CREATE TABLE users (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email      text NOT NULL UNIQUE,
    name       text NOT NULL,
    pw_hash    text NOT NULL,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE lists (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text NOT NULL,
    sort       integer NOT NULL DEFAULT 0,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE items (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id     uuid NOT NULL REFERENCES lists (id) ON DELETE CASCADE,
    text        text NOT NULL,
    description text,
    done        boolean NOT NULL DEFAULT false,
    status      todo_status NOT NULL DEFAULT 'NOT_STARTED',
    priority    smallint,
    due_at      timestamp,
    location    text,
    assignee_id uuid REFERENCES users (id),
    sort        integer NOT NULL DEFAULT 0,
    created_by  uuid REFERENCES users (id),
    created_at  timestamp NOT NULL DEFAULT now(),
    updated_at  timestamp NOT NULL DEFAULT now()
);
