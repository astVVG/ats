package io.github.astvvg.ats.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.astvvg.ats.candidate.service.ResumeStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResumeStorageTest {

    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @TempDir Path root;

    private Path base;
    private ResumeStorage storage;

    @BeforeEach
    void setUp() {
        base = root.resolve("uploads");
        storage = new ResumeStorage(base.toString());
    }

    private List<Path> filesUnder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void storeWritesGeneratedFileInsideCandidateFolder() throws IOException {
        String relative = storage.store(7, PDF);

        assertThat(relative).matches("resumes/7/[0-9a-f-]{36}\\.pdf");
        Path file = base.resolve(relative);
        assertThat(file).isRegularFile().hasBinaryContent(PDF);
        assertThat(storage.find(7, relative)).contains(file.toAbsolutePath().normalize());
    }

    @Test
    void replacingKeepsOnlyTheNewFile() throws IOException {
        String previous = storage.store(7, PDF);
        byte[] newer = "%PDF-1.7 newer".getBytes(StandardCharsets.US_ASCII);

        String current = storage.store(7, newer);
        storage.delete(7, previous);

        assertThat(current).isNotEqualTo(previous);
        assertThat(storage.find(7, previous)).isEmpty();
        assertThat(filesUnder(base.resolve("resumes/7")))
                .singleElement()
                .satisfies(file -> assertThat(file).hasBinaryContent(newer));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../evil.pdf",
                "../../evil.pdf",
                "../../../evil.pdf",
                "../8/evil.pdf",
                "sub/../../evil.pdf",
                ".",
                ""
            })
    void resolveRejectsNamesOutsideCandidateFolder(String fileName) throws IOException {
        assertThatThrownBy(() -> storage.resolve(7, fileName))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(filesUnder(root)).isEmpty();
    }

    @Test
    void resolveRejectsAbsolutePaths() {
        String outside = root.resolve("evil.pdf").toAbsolutePath().toString();
        assertThatThrownBy(() -> storage.resolve(7, outside))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveAcceptsPlainFileName() {
        assertThat(storage.resolve(7, "cv.pdf"))
                .isEqualTo(base.resolve("resumes/7/cv.pdf").toAbsolutePath().normalize());
    }

    @Test
    void storedPathsOutsideCandidateFolderAreRejected() throws IOException {
        String otherCandidate = storage.store(8, PDF);
        Path outside = Files.writeString(root.resolve("secret.txt"), "secret");

        for (String tampered :
                List.of(
                        otherCandidate,
                        "resumes/7/../8/" + Path.of(otherCandidate).getFileName(),
                        "resumes/7/../../../secret.txt",
                        outside.toAbsolutePath().toString())) {
            assertThatThrownBy(() -> storage.find(7, tampered))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(7, tampered))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(outside).exists();
        assertThat(storage.find(8, otherCandidate)).isPresent();
    }

    @Test
    void findOfMissingFileIsEmpty() {
        assertThat(storage.find(7, "resumes/7/missing.pdf")).isEmpty();
    }

    @Test
    void deleteCandidateFolderRemovesOnlyThatCandidate() throws IOException {
        storage.store(7, PDF);
        storage.store(7, PDF);
        String other = storage.store(8, PDF);

        storage.deleteCandidateFolder(7);

        assertThat(base.resolve("resumes/7")).doesNotExist();
        assertThat(storage.find(8, other)).isPresent();
    }

    @Test
    void deleteCandidateFolderWithoutFolderDoesNothing() {
        storage.deleteCandidateFolder(99);
        assertThat(base.resolve("resumes/99")).doesNotExist();
    }
}
