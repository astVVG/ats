package io.github.astvvg.ats.candidate.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores resume files under {@code {base}/resumes/{candidateId}/{uuid}.pdf}. File names are always
 * generated here, and every path is checked to stay inside the candidate's folder.
 */
@Component
public class ResumeStorage {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorage.class);
    private static final String RESUMES = "resumes";

    private final Path baseDir;

    public ResumeStorage(@Value("${app.uploads.dir}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    /** Writes the content to a new file and returns its path relative to the base directory. */
    public String store(long candidateId, byte[] content) {
        Path target = resolve(candidateId, UUID.randomUUID() + ".pdf");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store resume", ex);
        }
        return StreamSupport.stream(baseDir.relativize(target).spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
    }

    /** The stored file, if {@code relativePath} points to an existing file of this candidate. */
    public Optional<Path> find(long candidateId, String relativePath) {
        Path file = resolveStored(candidateId, relativePath);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Best effort: a file that cannot be removed is logged and left behind. */
    public void delete(long candidateId, String relativePath) {
        Path file = resolveStored(candidateId, relativePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete resume {}", file, ex);
        }
    }

    /** Best effort: removes the candidate's folder and everything in it, if it exists. */
    public void deleteCandidateFolder(long candidateId) {
        Path folder = candidateDir(candidateId);
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ex) {
                            log.warn("Could not delete {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            log.warn("Could not delete resume folder {}", folder, ex);
        }
    }

    /**
     * Resolves {@code fileName} inside the candidate's folder.
     *
     * @throws IllegalArgumentException if the result would be outside that folder
     */
    public Path resolve(long candidateId, String fileName) {
        Path folder = candidateDir(candidateId);
        return requireInside(folder, folder.resolve(fileName).normalize());
    }

    private Path resolveStored(long candidateId, String relativePath) {
        return requireInside(candidateDir(candidateId), baseDir.resolve(relativePath).normalize());
    }

    private Path candidateDir(long candidateId) {
        return baseDir.resolve(RESUMES).resolve(Long.toString(candidateId));
    }

    private static Path requireInside(Path folder, Path target) {
        if (!target.startsWith(folder) || target.equals(folder)) {
            throw new IllegalArgumentException("Resume path is outside the candidate folder");
        }
        return target;
    }
}
