package io.github.astvvg.ats.metrics.service;

import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.common.InvalidFieldException;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.metrics.dto.FunnelResponse;
import io.github.astvvg.ats.metrics.dto.FunnelStageResponse;
import io.github.astvvg.ats.metrics.dto.StageTimeResponse;
import io.github.astvvg.ats.metrics.dto.TimeInStageResponse;
import io.github.astvvg.ats.metrics.dto.TimeToHireResponse;
import io.github.astvvg.ats.metrics.repository.MetricsFilter;
import io.github.astvvg.ats.metrics.repository.MetricsRepository;
import io.github.astvvg.ats.metrics.repository.MetricsRepository.StageDurations;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MetricsService {

    /** The forward path of the pipeline; REJECTED is outside it. */
    static final List<Stage> SEQUENCE =
            List.of(Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER, Stage.HIRED);

    static final int CONVERSION_SCALE = 4;
    static final int AVERAGE_SCALE = 2;
    private static final long SECONDS_PER_HOUR = 3_600;
    private static final long SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;

    private final MetricsRepository metricsRepository;
    private final JobRepository jobRepository;
    private final ZoneId zone;

    public MetricsService(
            MetricsRepository metricsRepository,
            JobRepository jobRepository,
            @Value("${app.metrics.time-zone}") String timeZone) {
        this.metricsRepository = metricsRepository;
        this.jobRepository = jobRepository;
        this.zone = ZoneId.of(timeZone);
    }

    public FunnelResponse funnel(Long jobId, LocalDate from, LocalDate to) {
        Map<Stage, Long> reached = metricsRepository.reachedByStage(filter(jobId, from, to));
        List<FunnelStageResponse> stages =
                Arrays.stream(Stage.values())
                        .map(stage ->
                                new FunnelStageResponse(
                                        stage,
                                        reached.getOrDefault(stage, 0L),
                                        conversionToNext(stage, reached)))
                        .toList();
        return new FunnelResponse(stages);
    }

    public TimeInStageResponse timeInStage(Long jobId, LocalDate from, LocalDate to) {
        Map<Stage, StageDurations> stays = metricsRepository.finishedStays(filter(jobId, from, to));
        List<StageTimeResponse> stages =
                Arrays.stream(Stage.values())
                        .map(stage -> {
                            StageDurations durations = stays.get(stage);
                            if (durations == null || durations.samples() == 0) {
                                return new StageTimeResponse(stage, null, 0);
                            }
                            return new StageTimeResponse(
                                    stage,
                                    average(durations, SECONDS_PER_HOUR),
                                    durations.samples());
                        })
                        .toList();
        return new TimeInStageResponse(stages);
    }

    public TimeToHireResponse timeToHire(Long jobId, LocalDate from, LocalDate to) {
        StageDurations hired = metricsRepository.timeToHire(filter(jobId, from, to));
        if (hired.samples() == 0) {
            return new TimeToHireResponse(null, 0);
        }
        return new TimeToHireResponse(average(hired, SECONDS_PER_DAY), hired.samples());
    }

    /** Exact average of the summed seconds in {@code unit}, rounded once at the end. */
    private static BigDecimal average(StageDurations durations, long unit) {
        return durations
                .totalSeconds()
                .divide(
                        BigDecimal.valueOf(durations.samples() * unit),
                        AVERAGE_SCALE,
                        RoundingMode.HALF_UP);
    }

    private static BigDecimal conversionToNext(Stage stage, Map<Stage, Long> reached) {
        int index = SEQUENCE.indexOf(stage);
        if (index < 0 || index == SEQUENCE.size() - 1) {
            return null;
        }
        long current = reached.getOrDefault(stage, 0L);
        if (current == 0) {
            return null;
        }
        long next = reached.getOrDefault(SEQUENCE.get(index + 1), 0L);
        return BigDecimal.valueOf(next)
                .divide(BigDecimal.valueOf(current), CONVERSION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Validates the filters and turns the inclusive dates into [start of from, start of the day
     * after to) in the configured zone.
     */
    private MetricsFilter filter(Long jobId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFieldException("from", "must not be after to");
        }
        if (jobId != null && !jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Job " + jobId + " not found");
        }
        Instant createdFrom = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant createdBefore = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return new MetricsFilter(jobId, createdFrom, createdBefore);
    }
}
