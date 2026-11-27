package io.github.astvvg.ats.note.repository;

import io.github.astvvg.ats.note.domain.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

    /** One page of an application's notes with their authors, paginated in the database. */
    @EntityGraph(attributePaths = "author")
    Page<Note> findByApplicationId(Long applicationId, Pageable pageable);
}
