package io.github.astvvg.ats.application.service;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.domain.StageHistory;
import io.github.astvvg.ats.application.dto.ApplicationResponse;
import io.github.astvvg.ats.application.dto.CreateApplicationRequest;
import io.github.astvvg.ats.application.dto.MoveApplicationRequest;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.application.repository.StageHistoryRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.common.InvalidFieldException;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationService {

    static final String ALREADY_APPLIED = "The candidate has already applied to this job";
    static final String JOB_CLOSED = "The job is closed and does not accept applications";
    static final String JOB_CLOSED_FOR_MOVES = "The job is closed and its board cannot change";
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

    /**
     * Moves an application to {@code request.stage()} at {@code request.position()} and closes the
     * gaps it leaves, keeping positions zero-based and contiguous in both columns.
     */
    public ApplicationResponse move(
            Long id, MoveApplicationRequest request, Long userId, Role role) {
        Long jobId = applicationRepository.findJobIdById(id).orElseThrow(() -> notFound(id));
        // Concurrent moves on the same job wait here; everything below uses data read after it.
        Job job = jobRepository.findByIdForUpdate(jobId).orElseThrow(() -> notFound(id));
        Application application =
                applicationRepository.findById(id).orElseThrow(() -> notFound(id));
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BusinessConflictException(JOB_CLOSED_FOR_MOVES);
        }

        Stage from = application.getStage();
        int current = application.getPosition();
        Stage to = request.stage();
        int target = request.position();
        if (from == Stage.HIRED && to != Stage.HIRED && role != Role.ADMIN) {
            throw new AccessDeniedException("Only an admin can move an application out of HIRED");
        }
        long count = applicationRepository.countByJobIdAndStage(jobId, to);
        long max = from == to ? count - 1 : count;
        if (target > max) {
            throw new InvalidFieldException("position", "must be between 0 and " + max);
        }
        if (from == to && current == target) {
            return applicationMapper.toResponse(application);
        }

        if (from == to) {
            if (target > current) {
                applicationRepository.decrementPositions(jobId, from, current, target);
            } else {
                applicationRepository.incrementPositions(jobId, from, target, current);
            }
        } else {
            applicationRepository.decrementPositions(jobId, from, current, Integer.MAX_VALUE);
            applicationRepository.incrementPositions(jobId, to, target, Integer.MAX_VALUE);
        }
        // The bulk updates cleared the persistence context; load the row again.
        Application moved = applicationRepository.findById(id).orElseThrow(() -> notFound(id));
        moved.moveTo(to, target);
        applicationRepository.saveAndFlush(moved);
        if (from != to) {
            stageHistoryRepository.save(
                    new StageHistory(moved, from, to, userRepository.getReferenceById(userId)));
        }
        return applicationMapper.toResponse(moved);
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Application " + id + " not found");
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
