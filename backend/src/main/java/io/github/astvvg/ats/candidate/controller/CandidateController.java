package io.github.astvvg.ats.candidate.controller;

import io.github.astvvg.ats.candidate.dto.CandidateRequest;
import io.github.astvvg.ats.candidate.dto.CandidateResponse;
import io.github.astvvg.ats.candidate.service.CandidateService;
import io.github.astvvg.ats.common.OpenApiConfig;
import io.github.astvvg.ats.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/candidates")
@Tag(name = "Candidates", description = "Candidates and their resumes (ADMIN and RECRUITER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    @Operation(
            summary = "List candidates",
            description =
                    "Sortable by createdAt, fullName and email (default createdAt,desc; ties by id"
                            + " desc).")
    @ApiResponse(responseCode = "200", description = "Page of candidates")
    @ApiResponse(responseCode = "400", description = "Unsupported sort property")
    public PageResponse<CandidateResponse> list(
            @Parameter(description = "Case-insensitive text contained in full name or email")
                    @RequestParam(required = false)
                    String q,
            @ParameterObject Pageable pageable) {
        return candidateService.list(q, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a candidate")
    @ApiResponse(responseCode = "201", description = "Candidate created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "409", description = "Email already used by another candidate")
    public CandidateResponse create(@Valid @RequestBody CandidateRequest request) {
        return candidateService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a candidate")
    @ApiResponse(responseCode = "200", description = "Candidate found")
    @ApiResponse(responseCode = "404", description = "Candidate not found")
    public CandidateResponse get(@PathVariable Long id) {
        return candidateService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a candidate")
    @ApiResponse(responseCode = "200", description = "Candidate updated")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Candidate not found")
    @ApiResponse(responseCode = "409", description = "Email already used by another candidate")
    public CandidateResponse update(
            @PathVariable Long id, @Valid @RequestBody CandidateRequest request) {
        return candidateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a candidate and its resume")
    @ApiResponse(responseCode = "204", description = "Candidate deleted")
    @ApiResponse(responseCode = "404", description = "Candidate not found")
    public void delete(@PathVariable Long id) {
        candidateService.delete(id);
    }

    @PostMapping(path = "/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the candidate's resume (PDF, at most 5 MB)")
    @ApiResponse(responseCode = "200", description = "Resume stored")
    @ApiResponse(responseCode = "400", description = "Empty, too large or not a PDF")
    @ApiResponse(responseCode = "404", description = "Candidate not found")
    public CandidateResponse uploadResume(
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return candidateService.uploadResume(id, file);
    }

    @GetMapping("/{id}/resume")
    @Operation(summary = "Download the candidate's resume")
    @ApiResponse(
            responseCode = "200",
            description = "The resume PDF",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Candidate not found or without a resume")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long id) {
        Resource resume = new FileSystemResource(candidateService.findResume(id));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("resume-" + id + ".pdf")
                                .build()
                                .toString())
                .body(resume);
    }
}
