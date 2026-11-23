package io.github.astvvg.ats.job.controller;

import io.github.astvvg.ats.common.OpenApiConfig;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.dto.JobRequest;
import io.github.astvvg.ats.job.dto.JobResponse;
import io.github.astvvg.ats.job.dto.JobStatusRequest;
import io.github.astvvg.ats.job.service.JobService;
import io.github.astvvg.ats.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Job openings (ADMIN and RECRUITER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    @Operation(
            summary = "List jobs",
            description =
                    "Open and closed jobs unless status is given. Sortable by createdAt, updatedAt"
                            + " and title (default createdAt,desc; ties by id desc).")
    @ApiResponse(responseCode = "200", description = "Page of jobs")
    @ApiResponse(responseCode = "400", description = "Unsupported status or sort property")
    public PageResponse<JobResponse> list(
            @RequestParam(required = false) JobStatus status,
            @Parameter(description = "Case-insensitive text contained in title or department")
                    @RequestParam(required = false)
                    String q,
            @ParameterObject Pageable pageable) {
        return jobService.list(status, q, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a job; it starts OPEN")
    @ApiResponse(responseCode = "201", description = "Job created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    public JobResponse create(
            @Valid @RequestBody JobRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return jobService.create(request, user.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a job")
    @ApiResponse(responseCode = "200", description = "Job found")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public JobResponse get(@PathVariable Long id) {
        return jobService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update the title, department, location and description of a job")
    @ApiResponse(responseCode = "200", description = "Job updated")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return jobService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Open or close a job (idempotent)")
    @ApiResponse(responseCode = "200", description = "Job status set")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public JobResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody JobStatusRequest request) {
        return jobService.updateStatus(id, request);
    }
}
