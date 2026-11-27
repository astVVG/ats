package io.github.astvvg.ats.note.dto;

import io.github.astvvg.ats.note.domain.Note;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
        @Schema(
                        example = "Strong system design answers; schedule a second interview.",
                        maxLength = Note.MAX_CONTENT_LENGTH,
                        description =
                                "Stored without leading and trailing whitespace; the length limit"
                                        + " applies to the trimmed text.")
                @NotBlank
                @Size(max = Note.MAX_CONTENT_LENGTH)
                String content) {

    // Trimmed before validation runs, so the limits apply to what is stored.
    public CreateNoteRequest {
        content = content == null ? null : content.strip();
    }
}
