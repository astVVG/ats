package io.github.astvvg.ats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Reproduces losing the race between a service pre-check and its write: the pre-check is told that
 * nothing is there, so the write reaches MySQL and the real constraint has to produce the 409. Not
 * transactional, so the conflicting row is committed as a concurrent request would leave it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ConstraintRaceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @MockitoSpyBean private UserRepository userRepository;
    @MockitoSpyBean private CandidateRepository candidateRepository;
    @MockitoSpyBean private ApplicationRepository applicationRepository;

    private User admin;
    private String auth;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.ADMIN);
        auth = testUsers.bearer(admin);
    }

    private Candidate candidate() {
        return candidateRepository.saveAndFlush(
                new Candidate("Race", TestUsers.uniqueEmail("race"), null, null));
    }

    private Application application(Candidate candidate) {
        Job job = jobRepository.saveAndFlush(new Job("Race", null, null, null, admin));
        return applicationRepository.saveAndFlush(
                new Application(job, candidate, Stage.APPLIED, 0));
    }

    /** Status and problem detail of the answer. */
    private String answer(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, auth);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        }
        var response = mockMvc.perform(request).andReturn().getResponse();
        return response.getStatus()
                + " "
                + objectMapper.readTree(response.getContentAsString()).path("detail").asText();
    }

    @Test
    void userEmailTakenAfterTheCheckIsConflict() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "name",
                        "Race",
                        "email",
                        admin.getEmail(),
                        "password",
                        TestUsers.PASSWORD,
                        "role",
                        "RECRUITER");
        String checked = answer(post("/api/users"), body);
        doReturn(false).when(userRepository).existsByEmail(any());

        assertThat(answer(post("/api/users"), body)).startsWith("409 ").isEqualTo(checked);
    }

    @Test
    void candidateEmailTakenAfterTheCheckIsConflictOnCreateAndUpdate() throws Exception {
        Candidate existing = candidate();
        Candidate other = candidate();
        Map<String, Object> body = Map.of("fullName", "Race", "email", existing.getEmail());
        String checked = answer(post("/api/candidates"), body);
        doReturn(false).when(candidateRepository).existsByEmail(any());
        doReturn(false).when(candidateRepository).existsByEmailForOtherCandidate(any(), any());

        assertThat(answer(post("/api/candidates"), body)).startsWith("409 ").isEqualTo(checked);
        assertThat(answer(put("/api/candidates/" + other.getId()), body))
                .startsWith("409 ")
                .isEqualTo(checked);
    }

    @Test
    void secondApplicationCreatedAfterTheCheckIsConflict() throws Exception {
        Application existing = application(candidate());
        Map<String, Object> body =
                Map.of(
                        "jobId", existing.getJob().getId(),
                        "candidateId", existing.getCandidate().getId());
        String checked = answer(post("/api/applications"), body);
        doReturn(false).when(applicationRepository).existsByJobIdAndCandidateId(any(), any());

        assertThat(answer(post("/api/applications"), body)).startsWith("409 ").isEqualTo(checked);
    }

    @Test
    void applicationCreatedAfterTheCheckKeepsTheCandidate() throws Exception {
        Candidate candidate = candidate();
        application(candidate);
        String path = "/api/candidates/" + candidate.getId();
        String checked = answer(delete(path), null);
        doReturn(false).when(applicationRepository).existsByCandidateId(any());

        assertThat(answer(delete(path), null)).startsWith("409 ").isEqualTo(checked);
        assertThat(candidateRepository.existsById(candidate.getId())).isTrue();
    }
}
