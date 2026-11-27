package io.github.astvvg.ats.note.controller;

import io.github.astvvg.ats.common.OpenApiConfig;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.note.dto.CreateNoteRequest;
import io.github.astvvg.ats.note.dto.NoteResponse;
import io.github.astvvg.ats.note.service.NoteService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Notes", description = "Notes on applications (ADMIN and RECRUITER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/api/applications/{id}/notes")
    @Operation(
            summary = "List the notes of an application",
            description =
                    "Sortable by createdAt only (default createdAt,desc; ties by id in the same"
                            + " direction). Closed jobs are included.")
    @ApiResponse(responseCode = "200", description = "Page of notes")
    @ApiResponse(responseCode = "400", description = "Unsupported sort property")
    @ApiResponse(responseCode = "404", description = "Application not found")
    public PageResponse<NoteResponse> list(
            @PathVariable Long id, @ParameterObject Pageable pageable) {
        return noteService.list(id, pageable);
    }

    @PostMapping("/api/applications/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a note to an application",
            description =
                    "The author is the authenticated user. Allowed on applications of closed"
                            + " jobs.")
    @ApiResponse(responseCode = "201", description = "Note created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Application not found")
    public NoteResponse create(
            @PathVariable Long id,
            @Valid @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return noteService.create(id, request, user.getId());
    }

    @DeleteMapping("/api/notes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a note",
            description = "Only its author or an ADMIN can delete it.")
    @ApiResponse(responseCode = "204", description = "Note deleted")
    @ApiResponse(responseCode = "403", description = "Neither the author nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "Note not found")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        noteService.delete(id, user.getId(), user.getRole());
    }
}
