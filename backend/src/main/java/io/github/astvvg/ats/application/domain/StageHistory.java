package io.github.astvvg.ats.application.domain;

import io.github.astvvg.ats.user.domain.User;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** One stage change of an application; {@code fromStage} is null for the initial entry. */
@Entity
@Table(name = "stage_history")
public class StageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20, updatable = false)
    private Stage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 20, updatable = false)
    private Stage toStage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moved_by", nullable = false, updatable = false)
    private User movedBy;

    @Column(name = "moved_at", nullable = false, updatable = false)
    private Instant movedAt;

    protected StageHistory() {}

    public StageHistory(Application application, Stage fromStage, Stage toStage, User movedBy) {
        this.application = application;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.movedBy = movedBy;
    }

    @PrePersist
    void onCreate() {
        movedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public Stage getFromStage() {
        return fromStage;
    }

    public Stage getToStage() {
        return toStage;
    }

    public User getMovedBy() {
        return movedBy;
    }

    public Instant getMovedAt() {
        return movedAt;
    }
}
