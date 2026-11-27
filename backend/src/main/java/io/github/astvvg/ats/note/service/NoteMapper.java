package io.github.astvvg.ats.note.service;

import io.github.astvvg.ats.note.domain.Note;
import io.github.astvvg.ats.note.dto.NoteAuthorResponse;
import io.github.astvvg.ats.note.dto.NoteResponse;
import io.github.astvvg.ats.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NoteMapper {

    @Mapping(target = "applicationId", source = "application.id")
    NoteResponse toResponse(Note note);

    NoteAuthorResponse toAuthor(User user);
}
