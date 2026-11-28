package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.domain.StageHistory;
import io.github.astvvg.ats.application.dto.ApplicationResponse;
import io.github.astvvg.ats.application.dto.MoveApplicationRequest;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.application.repository.StageHistoryRepository;
import io.github.astvvg.ats.application.service.ApplicationMapper;
import io.github.astvvg.ats.application.service.ApplicationService;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.common.InvalidFieldException;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationMoveServiceTest {

    private static final long JOB_ID = 10L;
    private static final long APPLICATION_ID = 20L;
    private static final long USER_ID = 30L;
    private static final int MAX = Integer.MAX_VALUE;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private StageHistoryRepository stageHistoryRepository;
    @Mock private JobRepository jobRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private UserRepository userRepository;

    private ApplicationService applicationService;
    private Job job;

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
        job = new Job("Job", null, null, null, null);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
    }

    /** Stubs an application at {@code stage}/{@code position} in a column of {@code count}. */
    private Application existing(Stage stage, int position, Stage target, long targetCount) {
        Application application =
                new Application(
                        job, new Candidate("C", "c@example.com", null, null), stage, position);
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        when(applicationRepository.findJobIdById(APPLICATION_ID)).thenReturn(Optional.of(JOB_ID));
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(applicationRepository.countByJobIdAndStage(JOB_ID, target)).thenReturn(targetCount);
        return application;
    }

    private ApplicationResponse move(Stage stage, int position, Role role) {
        return applicationService.move(
                APPLICATION_ID, new MoveApplicationRequest(stage, position), USER_ID, role);
    }

    private void verifyNoChanges() {
        verify(applicationRepository, never())
                .decrementPositions(anyLong(), any(), anyInt(), anyInt());
        verify(applicationRepository, never())
                .incrementPositions(anyLong(), any(), anyInt(), anyInt());
        verify(applicationRepository, never()).saveAndFlush(any());
        verify(stageHistoryRepository, never()).save(any());
    }

    @Test
    void reorderDownInSameStageShiftsTheRangeUpAndRecordsNoHistory() {
        existing(Stage.APPLIED, 1, Stage.APPLIED, 5);

        ApplicationResponse response = move(Stage.APPLIED, 3, Role.RECRUITER);

        verify(applicationRepository).decrementPositions(JOB_ID, Stage.APPLIED, 1, 3);
        verify(applicationRepository, never())
                .incrementPositions(anyLong(), any(), anyInt(), anyInt());
        verify(stageHistoryRepository, never()).save(any());
        assertThat(response.stage()).isEqualTo(Stage.APPLIED);
        assertThat(response.position()).isEqualTo(3);
    }

    @Test
    void reorderUpInSameStageShiftsTheRangeDown() {
        existing(Stage.OFFER, 4, Stage.OFFER, 5);

        assertThat(move(Stage.OFFER, 0, Role.RECRUITER).position()).isZero();

        verify(applicationRepository).incrementPositions(JOB_ID, Stage.OFFER, 0, 4);
        verify(applicationRepository, never())
                .decrementPositions(anyLong(), any(), anyInt(), anyInt());
        verify(stageHistoryRepository, never()).save(any());
    }

    @Test
    void moveToAnotherStageClosesTheGapThenOpensOneAndRecordsHistory() {
        Application application = existing(Stage.APPLIED, 2, Stage.INTERVIEW, 3);
        User mover = new User("Mover", "mover@example.com", "hash", Role.RECRUITER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mover);

        ApplicationResponse response = move(Stage.INTERVIEW, 3, Role.RECRUITER);

        InOrder order = inOrder(applicationRepository);
        order.verify(applicationRepository).decrementPositions(JOB_ID, Stage.APPLIED, 2, MAX);
        order.verify(applicationRepository).incrementPositions(JOB_ID, Stage.INTERVIEW, 3, MAX);
        order.verify(applicationRepository).saveAndFlush(application);
        ArgumentCaptor<StageHistory> history = ArgumentCaptor.forClass(StageHistory.class);
        verify(stageHistoryRepository).save(history.capture());
        assertThat(history.getValue().getFromStage()).isEqualTo(Stage.APPLIED);
        assertThat(history.getValue().getToStage()).isEqualTo(Stage.INTERVIEW);
        assertThat(history.getValue().getMovedBy()).isSameAs(mover);
        assertThat(response.stage()).isEqualTo(Stage.INTERVIEW);
        assertThat(response.position()).isEqualTo(3);
    }

    @Test
    void samePositionInSameStageIsANoOp() {
        existing(Stage.INTERVIEW, 1, Stage.INTERVIEW, 3);

        ApplicationResponse response = move(Stage.INTERVIEW, 1, Role.RECRUITER);

        assertThat(response.position()).isEqualTo(1);
        verifyNoChanges();
    }

    @Test
    void positionBeyondTheEndOfTheSameStageIsInvalid() {
        existing(Stage.APPLIED, 0, Stage.APPLIED, 3);

        assertThatThrownBy(() -> move(Stage.APPLIED, 3, Role.ADMIN))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage("debe estar entre 0 y 2")
                .extracting("field")
                .isEqualTo("position");
        verifyNoChanges();
    }

    @Test
    void positionBeyondTheEndOfAnotherStageIsInvalid() {
        existing(Stage.APPLIED, 0, Stage.OFFER, 2);

        assertThatThrownBy(() -> move(Stage.OFFER, 3, Role.ADMIN))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage("debe estar entre 0 y 2");
        verifyNoChanges();
    }

    @Test
    void recruiterCannotMoveOutOfHired() {
        Application application = new Application(job, null, Stage.HIRED, 0);
        when(applicationRepository.findJobIdById(APPLICATION_ID)).thenReturn(Optional.of(JOB_ID));
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> move(Stage.OFFER, 0, Role.RECRUITER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoChanges();
    }

    @Test
    void adminCanMoveOutOfHired() {
        existing(Stage.HIRED, 0, Stage.REJECTED, 0);
        assertThat(move(Stage.REJECTED, 0, Role.ADMIN).stage()).isEqualTo(Stage.REJECTED);
    }

    @Test
    void recruiterCanReorderInsideHired() {
        existing(Stage.HIRED, 0, Stage.HIRED, 2);

        assertThat(move(Stage.HIRED, 1, Role.RECRUITER).position()).isEqualTo(1);
        verify(applicationRepository).decrementPositions(JOB_ID, Stage.HIRED, 0, 1);
    }

    @Test
    void closedJobIsConflict() {
        job.setStatus(JobStatus.CLOSED);
        when(applicationRepository.findJobIdById(APPLICATION_ID)).thenReturn(Optional.of(JOB_ID));
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(applicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(new Application(job, null, Stage.APPLIED, 0)));

        assertThatThrownBy(() -> move(Stage.INTERVIEW, 0, Role.ADMIN))
                .isInstanceOf(BusinessConflictException.class);
        verifyNoChanges();
    }

    @Test
    void missingApplicationIsNotFoundBeforeLocking() {
        when(applicationRepository.findJobIdById(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> move(Stage.INTERVIEW, 0, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(jobRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void applicationDeletedWhileWaitingForTheLockIsNotFound() {
        when(applicationRepository.findJobIdById(APPLICATION_ID)).thenReturn(Optional.of(JOB_ID));
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> move(Stage.INTERVIEW, 0, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoChanges();
    }
}
