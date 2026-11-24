package io.github.astvvg.ats.application.service;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.domain.StageHistory;
import io.github.astvvg.ats.application.dto.ApplicationResponse;
import io.github.astvvg.ats.application.dto.CreateApplicationRequest;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.application.repository.StageHistoryRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationService {

    static final String ALREADY_APPLIED = "The candidate has already applied to this job";
    static final String JOB_CLOSED = "The job is closed and does not accept applications";
    private static final String UNIQUE_CONSTRAINT = "uq_applications_job_candidate";

    private final ApplicationRepository applicationRepository;
    private final StageHistoryRepository stageHistoryRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final ApplicationMapper applicationMapper;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            StageHistoryRepository stageHistoryRepository,
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            ApplicationMapper applicationMapper) {
        this.applicationRepository = applicationRepository;
        this.stageHistoryRepository = stageHistoryRepository;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.applicationMapper = applicationMapper;
    }

    /** Adds the candidate at the end of the job's APPLIED column and records the first stage. */
    public ApplicationResponse create(CreateApplicationRequest request, Long userId) {
        // The lock makes concurrent creations for the same job take positions one at a time.
        Job job =
                jobRepository
                        .findByIdForUpdate(request.jobId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Job " + request.jobId() + " not found"));
        Candidate candidate =
                candidateRepository
                        .findById(request.candidateId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Candidate " + request.candidateId() + " not found"));
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BusinessConflictException(JOB_CLOSED);
        }
        if (applicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId())) {
            throw new BusinessConflictException(ALREADY_APPLIED);
        }
        int position =
                applicationRepository
                        .findMaxPosition(job.getId(), Stage.APPLIED)
                        .map(max -> max + 1)
                        .orElse(0);

        Application application = new Application(job, candidate, Stage.APPLIED, position);
        try {
            applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, UNIQUE_CONSTRAINT)) {
                throw new BusinessConflictException(ALREADY_APPLIED);
            }
            throw ex;
        }
        stageHistoryRepository.save(
                new StageHistory(
                        application, null, Stage.APPLIED, userRepository.getReferenceById(userId)));
        return applicationMapper.toResponse(application);
    }

    static boolean violates(Throwable ex, String constraint) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
