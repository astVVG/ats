package io.github.astvvg.ats.note.domain;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A comment on an application; notes are not edited after they are written. */
@Entity
@Table(name = "notes")
public class Note {

    public static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH, updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Note() {}

    public Note(Application application, User author, String content) {
        this.application = application;
        this.author = author;
        this.content = content;
    }

    // Truncated to the precision of TIMESTAMPTZ so responses match what is stored.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
