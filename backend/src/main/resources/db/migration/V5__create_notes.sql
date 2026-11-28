-- applications.rating already has ck_applications_rating (V4), so no rating change is needed here.
CREATE TABLE notes (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT        NOT NULL,
    author_id      BIGINT        NOT NULL,
    content        VARCHAR(2000) NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    CONSTRAINT fk_notes_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_notes_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_as_ci;

CREATE INDEX idx_notes_application_id ON notes (application_id);
