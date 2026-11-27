package io.github.astvvg.ats.metrics.controller;

import io.github.astvvg.ats.common.OpenApiConfig;
import io.github.astvvg.ats.metrics.dto.FunnelResponse;
import io.github.astvvg.ats.metrics.dto.TimeInStageResponse;
import io.github.astvvg.ats.metrics.dto.TimeToHireResponse;
import io.github.astvvg.ats.metrics.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@Tag(
        name = "Metrics",
        description =
                "Hiring pipeline metrics (ADMIN and RECRUITER). Every filter is optional:"
                        + " jobId (closed jobs included), and from and to as inclusive dates"
                        + " compared against the application's creation day in the configured"
                        + " time zone. An included application contributes its whole history.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "400", description = "Invalid jobId or date, or from after to")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
@ApiResponse(responseCode = "404", description = "Job not found")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/funnel")
    @Operation(
            summary = "Hiring funnel",
            description =
                    "How many distinct applications ever reached each stage, and the conversion"
                            + " to the next one.")
    @ApiResponse(responseCode = "200", description = "Funnel with the five stages")
    public FunnelResponse funnel(
            @Parameter(description = "Only applications of this job")
                    @RequestParam(required = false)
                    Long jobId,
            @Parameter(description = "Applications created on or after this day (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Applications created on or before this day (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return metricsService.funnel(jobId, from, to);
    }

    @GetMapping("/time-in-stage")
    @Operation(
            summary = "Average time in each stage",
            description =
                    "Each finished stay is one sample: from entering the stage to the next move of"
                            + " the same application. Current stays are not counted.")
    @ApiResponse(responseCode = "200", description = "Average hours for the five stages")
    public TimeInStageResponse timeInStage(
            @Parameter(description = "Only applications of this job")
                    @RequestParam(required = false)
                    Long jobId,
            @Parameter(description = "Applications created on or after this day (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Applications created on or before this day (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return metricsService.timeInStage(jobId, from, to);
    }

    @GetMapping("/time-to-hire")
    @Operation(
            summary = "Average time to hire",
            description =
                    "Days from the creation of each hired application to its first arrival in"
                            + " HIRED; leaving HIRED later does not change it.")
    @ApiResponse(responseCode = "200", description = "Average days and number of hires")
    public TimeToHireResponse timeToHire(
            @Parameter(description = "Only applications of this job")
                    @RequestParam(required = false)
                    Long jobId,
            @Parameter(description = "Applications created on or after this day (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Applications created on or before this day (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return metricsService.timeToHire(jobId, from, to);
    }
}
