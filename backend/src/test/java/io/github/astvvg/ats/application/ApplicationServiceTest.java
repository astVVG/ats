package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.dto.CreateApplicationRequest;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.application.repository.StageHistoryRepository;
import io.github.astvvg.ats.application.service.ApplicationMapper;
import io.github.astvvg.ats.application.service.ApplicationService;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private StageHistoryRepository stageHistoryRepository;
    @Mock private JobRepository jobRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private UserRepository userRepository;

    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService =
                new ApplicationService(
                        applicationRepository,
                        stageHistoryRepository,
                        jobRepository,
                        candidateRepository,
                        userRepository,
                        Mappers.getMapper(ApplicationMapper.class));
    }

    @Test
    void uniqueViolationFromConcurrentInsertIsConflictWithoutHistory() {
        Job job = new Job("Job", null, null, null, null);
        ReflectionTestUtils.setField(job, "id", 1L);
        Candidate candidate = new Candidate("Cand", "cand@example.com", null, null);
        ReflectionTestUtils.setField(candidate, "id", 2L);
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(candidateRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(applicationRepository.existsByJobIdAndCandidateId(1L, 2L)).thenReturn(false);
        when(applicationRepository.findMaxPosition(1L, Stage.APPLIED)).thenReturn(Optional.empty());
        when(applicationRepository.saveAndFlush(any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate",
                                new ConstraintViolationException(
                                        "duplicate",
                                        new SQLException("duplicate"),
                                        "uq_applications_job_candidate")));

        assertThatThrownBy(
                        () -> applicationService.create(new CreateApplicationRequest(1L, 2L), 3L))
                .isInstanceOf(BusinessConflictException.class);
        verify(stageHistoryRepository, never()).save(any());
    }
}
