package io.github.astvvg.ats.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.candidate.service.CandidateService;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class CandidateResumeIntegrationTest {

    private static final byte[] PDF =
            "%PDF-1.4\nfirst resume\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    // Never ./uploads: every file written by these tests stays in a temporary folder.
    @TempDir static Path root;

    @DynamicPropertySource
    static void uploadsDir(DynamicPropertyRegistry registry) {
        registry.add("app.uploads.dir", () -> root.resolve("uploads").toString());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;

    private String adminAuth;
    private String recruiterAuth;

    @BeforeEach
    void setUp() {
        adminAuth = testUsers.bearer(testUsers.create(Role.ADMIN));
        recruiterAuth = testUsers.bearer(testUsers.create(Role.RECRUITER));
    }

    private long createCandidate() throws Exception {
        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "fullName",
                                "Resume Test",
                                "email",
                                "resume-" + UUID.randomUUID() + "@example.com"));
        String json =
                mockMvc.perform(
                                post("/api/candidates")
                                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private ResultActions upload(long id, String auth, MockMultipartFile file) throws Exception {
        return mockMvc.perform(
                multipart("/api/candidates/" + id + "/resume")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, auth));
    }

    private ResultActions download(long id, String auth) throws Exception {
        return mockMvc.perform(
                get("/api/candidates/" + id + "/resume").header(HttpHeaders.AUTHORIZATION, auth));
    }

    private static Path candidateDir(long id) {
        return root.resolve("uploads").resolve("resumes").resolve(Long.toString(id));
    }

    private static List<Path> filesUnder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static void assertBadFile(ResultActions result, String detail) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    @Test
    void uploadThenDownloadReturnsSameBytes() throws Exception {
        long id = createCandidate();

        String body =
                upload(id, recruiterAuth, file("cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id))
                        .andExpect(jsonPath("$.hasResume").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("resumePath").doesNotContain("resumes/");

        byte[] downloaded =
                download(id, adminAuth)
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"resume-" + id + ".pdf\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);

        mockMvc.perform(get("/api/candidates/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.hasResume").value(true));
        assertThat(filesUnder(candidateDir(id)))
                .singleElement()
                .satisfies(
                        f -> assertThat(f.getFileName().toString()).matches("[0-9a-f-]{36}\\.pdf"));
    }

    @Test
    void uploadReplacesPreviousResume() throws Exception {
        long id = createCandidate();
        byte[] second = "%PDF-1.7\nsecond resume".getBytes(StandardCharsets.US_ASCII);

        upload(id, adminAuth, file("a.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                .andExpect(status().isOk());
        upload(id, recruiterAuth, file("b.pdf", MediaType.APPLICATION_PDF_VALUE, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResume").value(true));

        assertThat(filesUnder(candidateDir(id)))
                .singleElement()
                .satisfies(f -> assertThat(f).hasBinaryContent(second));
        assertThat(download(id, adminAuth).andReturn().getResponse().getContentAsByteArray())
                .isEqualTo(second);
    }

    @Test
    void textFileRenamedToPdfIsRejected() throws Exception {
        long id = createCandidate();

        assertBadFile(
                upload(
                        id,
                        adminAuth,
                        file(
                                "cv.pdf",
                                MediaType.APPLICATION_PDF_VALUE,
                                "just some text".getBytes(StandardCharsets.UTF_8))),
                "The file is not a PDF");

        assertThat(filesUnder(candidateDir(id))).isEmpty();
        mockMvc.perform(get("/api/candidates/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.hasResume").value(false));
    }

    @Test
    void contentDecidesNotNameOrContentType() throws Exception {
        long id = createCandidate();
        upload(id, adminAuth, file("cv.txt", MediaType.TEXT_PLAIN_VALUE, PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResume").value(true));
    }

    @Test
    void fileOverFiveMegabytesIsRejected() throws Exception {
        long id = createCandidate();
        byte[] big = Arrays.copyOf(PDF, (int) CandidateService.MAX_RESUME_BYTES + 1);

        assertBadFile(
                upload(id, adminAuth, file("big.pdf", MediaType.APPLICATION_PDF_VALUE, big)),
                "The file exceeds the maximum size of 5 MB");
        assertThat(filesUnder(candidateDir(id))).isEmpty();
    }

    @Test
    void fileOfExactlyFiveMegabytesIsAccepted() throws Exception {
        long id = createCandidate();
        byte[] max = Arrays.copyOf(PDF, (int) CandidateService.MAX_RESUME_BYTES);

        upload(id, adminAuth, file("max.pdf", MediaType.APPLICATION_PDF_VALUE, max))
                .andExpect(status().isOk());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        long id = createCandidate();
        assertBadFile(
                upload(
                        id,
                        adminAuth,
                        file("empty.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0])),
                "The file is empty");
    }

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        long id = createCandidate();
        mockMvc.perform(
                        multipart("/api/candidates/" + id + "/resume")
                                .file(new MockMultipartFile("other", "cv.pdf", null, PDF))
                                .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void clientFileNameCannotEscapeCandidateFolder() throws Exception {
        long id = createCandidate();

        for (String name : List.of("../../evil.pdf", "..\\..\\evil.pdf", "/tmp/evil.pdf")) {
            upload(id, adminAuth, file(name, MediaType.APPLICATION_PDF_VALUE, PDF))
                    .andExpect(status().isOk());
        }

        assertThat(filesUnder(candidateDir(id)))
                .singleElement()
                .satisfies(
                        f -> assertThat(f.getFileName().toString()).matches("[0-9a-f-]{36}\\.pdf"));
        assertThat(filesUnder(root))
                .allSatisfy(f -> {
                    assertThat(f).startsWith(root.resolve("uploads").resolve("resumes"));
                    assertThat(f.getFileName().toString()).doesNotContain("evil");
                });
        assertThat(root.resolve("evil.pdf")).doesNotExist();
        assertThat(root.resolve("uploads").resolve("evil.pdf")).doesNotExist();
    }

    @Test
    void downloadWithoutResumeIsNotFound() throws Exception {
        long id = createCandidate();
        download(id, recruiterAuth)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void missingCandidateIsNotFound() throws Exception {
        download(Long.MAX_VALUE, adminAuth).andExpect(status().isNotFound());
        upload(Long.MAX_VALUE, adminAuth, file("cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(candidateDir(Long.MAX_VALUE)).doesNotExist();
    }

    @Test
    void deletingCandidateRemovesResumeFolder() throws Exception {
        long id = createCandidate();
        upload(id, adminAuth, file("cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                .andExpect(status().isOk());
        assertThat(candidateDir(id)).isDirectory();

        mockMvc.perform(
                        delete("/api/candidates/" + id)
                                .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNoContent());

        assertThat(candidateDir(id)).doesNotExist();
    }
}
