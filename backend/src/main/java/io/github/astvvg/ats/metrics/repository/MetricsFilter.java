package io.github.astvvg.ats.metrics.repository;

import java.time.Instant;

/**
 * Which applications the metrics include. Every field is optional: {@code createdFrom} is inclusive
 * and {@code createdBefore} exclusive, both compared against applications.created_at.
 */
public record MetricsFilter(Long jobId, Instant createdFrom, Instant createdBefore) {}
