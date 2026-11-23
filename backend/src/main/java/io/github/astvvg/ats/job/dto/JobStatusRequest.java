package io.github.astvvg.ats.job.dto;

import io.github.astvvg.ats.job.domain.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusRequest(@NotNull JobStatus status) {}
