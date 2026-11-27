package io.github.astvvg.ats.application.domain;

import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.job.domain.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Stage stage;

    @Column(nullable = false)
    private int position;

    private Short rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Application() {}

    public Application(Job job, Candidate candidate, Stage stage, int position) {
        this.job = job;
        this.candidate = candidate;
        this.stage = stage;
        this.position = position;
    }

    // Truncated to the precision of TIMESTAMPTZ so responses match what is stored.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = now();
    }

    public void moveTo(Stage stage, int position) {
        this.stage = stage;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public Stage getStage() {
        return stage;
    }

    public int getPosition() {
        return position;
    }

    public Short getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
