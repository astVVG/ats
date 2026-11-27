package io.github.astvvg.ats.application.controller;

import io.github.astvvg.ats.application.dto.ApplicationResponse;
import io.github.astvvg.ats.application.dto.CreateApplicationRequest;
import io.github.astvvg.ats.application.dto.MoveApplicationRequest;
import io.github.astvvg.ats.application.dto.StageHistoryResponse;
import io.github.astvvg.ats.application.dto.UpdateRatingRequest;
import io.github.astvvg.ats.application.service.ApplicationService;
import io.github.astvvg.ats.common.OpenApiConfig;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Candidates applying to jobs (ADMIN and RECRUITER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Apply a candidate to a job",
            description =
                    "The application starts in APPLIED at the end of that column and its first"
                            + " stage history entry is recorded.")
    @ApiResponse(responseCode = "201", description = "Application created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Job or candidate not found")
    @ApiResponse(
            responseCode = "409",
            description = "The job is closed or the candidate already applied to it")
    public ApplicationResponse create(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return applicationService.create(request, user.getId());
    }

    @PatchMapping("/{id}/move")
    @Operation(
            summary = "Move an application on the board",
            description =
                    "Changes its stage, its position inside the column, or both, and reindexes"
                            + " the affected columns. A stage change records a stage history"
                            + " entry. Moving to the current stage and position changes nothing."
                            + " Only an ADMIN can move an application out of HIRED.")
    @ApiResponse(responseCode = "200", description = "Application moved")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request body or position outside the allowed range")
    @ApiResponse(
            responseCode = "403",
            description = "A RECRUITER tried to move the application out of HIRED")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "409", description = "The job is closed")
    public ApplicationResponse move(
            @PathVariable Long id,
            @Valid @RequestBody MoveApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return applicationService.move(id, request, user.getId(), user.getRole());
    }

    @PatchMapping("/{id}/rating")
    @Operation(
            summary = "Rate an application",
            description =
                    "Sets a rating from 1 to 5, or removes it with null. An empty body {} is the"
                            + " same as {\"rating\": null}. Setting the current value changes"
                            + " nothing, not even updatedAt. Records no stage history and is"
                            + " allowed on closed jobs.")
    @ApiResponse(responseCode = "200", description = "Rating set")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request body: rating outside 1 to 5 or not a whole number")
    @ApiResponse(responseCode = "404", description = "Application not found")
    public ApplicationResponse rate(
            @PathVariable Long id, @Valid @RequestBody UpdateRatingRequest request) {
        return applicationService.rate(id, request);
    }

    @GetMapping("/{id}/history")
    @Operation(
            summary = "List the stage history of an application",
            description =
                    "Every stage change, including the initial entry with a null fromStage."
                            + " Reordering inside a stage records nothing. Sortable by movedAt"
                            + " only (default movedAt,desc; ties by id in the same direction).")
    @ApiResponse(responseCode = "200", description = "Page of stage changes")
    @ApiResponse(responseCode = "400", description = "Unsupported sort property")
    @ApiResponse(responseCode = "404", description = "Application not found")
    public PageResponse<StageHistoryResponse> history(
            @PathVariable Long id, @ParameterObject Pageable pageable) {
        return applicationService.history(id, pageable);
    }
}
