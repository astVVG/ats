-- applications.rating already has ck_applications_rating (V4), so no rating change is needed here.
CREATE TABLE notes (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT        NOT NULL,
    author_id      BIGINT        NOT NULL,
    content        VARCHAR(2000) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_notes_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_notes_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_notes_application_id ON notes (application_id);
