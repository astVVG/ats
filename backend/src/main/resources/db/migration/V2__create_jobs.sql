CREATE TABLE jobs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    department  VARCHAR(100),
    location    VARCHAR(100),
    description TEXT,
    status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_jobs_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX idx_jobs_status ON jobs (status);
