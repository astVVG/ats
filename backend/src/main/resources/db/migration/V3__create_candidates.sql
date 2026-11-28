CREATE TABLE candidates (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(200) NOT NULL,
    email       VARCHAR(254) NOT NULL,
    phone       VARCHAR(30),
    source      VARCHAR(100),
    resume_path VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_candidates_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_as_ci;
