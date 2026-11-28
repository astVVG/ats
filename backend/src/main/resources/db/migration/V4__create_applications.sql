CREATE TABLE applications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id       BIGINT      NOT NULL,
    candidate_id BIGINT      NOT NULL,
    stage        VARCHAR(20) NOT NULL,
    position     INT         NOT NULL,
    rating       SMALLINT,
    created_at   DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at   DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_applications_job_candidate UNIQUE (job_id, candidate_id),
    CONSTRAINT ck_applications_stage
        CHECK (stage IN ('APPLIED', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    CONSTRAINT ck_applications_position CHECK (position >= 0),
    CONSTRAINT ck_applications_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_as_ci;

CREATE INDEX idx_applications_job_stage_position ON applications (job_id, stage, position);
CREATE INDEX idx_applications_candidate_id ON applications (candidate_id);

CREATE TABLE stage_history (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT      NOT NULL,
    from_stage     VARCHAR(20),
    to_stage       VARCHAR(20) NOT NULL,
    moved_by       BIGINT      NOT NULL,
    moved_at       DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT ck_stage_history_from_stage
        CHECK (from_stage IN ('APPLIED', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    CONSTRAINT ck_stage_history_to_stage
        CHECK (to_stage IN ('APPLIED', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    CONSTRAINT fk_stage_history_application
        FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_stage_history_moved_by FOREIGN KEY (moved_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_as_ci;

CREATE INDEX idx_stage_history_application_id ON stage_history (application_id);
