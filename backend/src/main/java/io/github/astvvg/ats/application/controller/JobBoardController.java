package io.github.astvvg.ats.application.controller;

import io.github.astvvg.ats.application.dto.BoardResponse;
import io.github.astvvg.ats.application.service.BoardService;
import io.github.astvvg.ats.common.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Board", description = "Hiring pipeline of a job (ADMIN and RECRUITER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class JobBoardController {

    private final BoardService boardService;

    public JobBoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/api/jobs/{id}/board")
    @Operation(
            summary = "Get the board of a job",
            description =
                    "Always five columns in the order APPLIED, INTERVIEW, OFFER, HIRED, REJECTED,"
                            + " each with its applications ordered by position. Closed jobs"
                            + " are included.")
    @ApiResponse(responseCode = "200", description = "Board of the job")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public BoardResponse get(@PathVariable Long id) {
        return boardService.get(id);
    }
}
