CREATE TABLE jobs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    department  VARCHAR(100),
    location    VARCHAR(100),
    description TEXT,
    status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT ck_jobs_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_as_ci;

CREATE INDEX idx_jobs_status ON jobs (status);
