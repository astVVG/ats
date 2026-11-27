package io.github.astvvg.ats.note.service;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.common.PageRequests;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.note.domain.Note;
import io.github.astvvg.ats.note.dto.CreateNoteRequest;
import io.github.astvvg.ats.note.dto.NoteResponse;
import io.github.astvvg.ats.note.repository.NoteRepository;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notes are allowed on applications of open and closed jobs alike. */
@Service
@Transactional
public class NoteService {

    static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final NoteRepository noteRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final NoteMapper noteMapper;

    public NoteService(
            NoteRepository noteRepository,
            ApplicationRepository applicationRepository,
            UserRepository userRepository,
            NoteMapper noteMapper) {
        this.noteRepository = noteRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.noteMapper = noteMapper;
    }

    /** The author is always the authenticated user. */
    public NoteResponse create(Long applicationId, CreateNoteRequest request, Long authorId) {
        Application application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> applicationNotFound(applicationId));
        Note note =
                new Note(application, userRepository.getReferenceById(authorId), request.content());
        return noteMapper.toResponse(noteRepository.saveAndFlush(note));
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteResponse> list(Long applicationId, Pageable pageable) {
        if (!applicationRepository.existsById(applicationId)) {
            throw applicationNotFound(applicationId);
        }
        Pageable sorted =
                PageRequests.withSortTiedById(pageable, SORTABLE_PROPERTIES, DEFAULT_SORT);
        return PageResponse.from(
                noteRepository
                        .findByApplicationId(applicationId, sorted)
                        .map(noteMapper::toResponse));
    }

    /** Only the author or an admin may delete a note; a missing note is reported first. */
    public void delete(Long id, Long userId, Role role) {
        Note note =
                noteRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Note " + id + " not found"));
        if (role != Role.ADMIN && !note.getAuthor().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        noteRepository.delete(note);
    }

    private static ResourceNotFoundException applicationNotFound(Long id) {
        return new ResourceNotFoundException("Application " + id + " not found");
    }
}
