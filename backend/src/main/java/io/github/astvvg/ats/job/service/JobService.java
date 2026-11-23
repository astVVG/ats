package io.github.astvvg.ats.job.service;

import io.github.astvvg.ats.common.PageRequests;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.dto.JobRequest;
import io.github.astvvg.ats.job.dto.JobResponse;
import io.github.astvvg.ats.job.dto.JobStatusRequest;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class JobService {

    static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "updatedAt", "title");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobMapper jobMapper;

    public JobService(
            JobRepository jobRepository, UserRepository userRepository, JobMapper jobMapper) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.jobMapper = jobMapper;
    }

    /** Lists open and closed jobs unless {@code status} narrows it. */
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(JobStatus status, String q, Pageable pageable) {
        Pageable sorted = PageRequests.withSort(pageable, SORTABLE_PROPERTIES, DEFAULT_SORT);
        return PageResponse.from(
                jobRepository
                        .findAll(JobRepository.matching(status, q), sorted)
                        .map(jobMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return jobMapper.toResponse(find(id));
    }

    public JobResponse create(JobRequest request, Long creatorId) {
        User creator =
                userRepository
                        .findById(creatorId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Job job =
                new Job(
                        request.title().trim(),
                        trimToNull(request.department()),
                        trimToNull(request.location()),
                        trimToNull(request.description()),
                        creator);
        return jobMapper.toResponse(jobRepository.saveAndFlush(job));
    }

    /** Replaces the editable fields; status and creator are left as they are. */
    public JobResponse update(Long id, JobRequest request) {
        Job job = find(id);
        job.update(
                request.title().trim(),
                trimToNull(request.department()),
                trimToNull(request.location()),
                trimToNull(request.description()));
        // Flush so the @PreUpdate timestamp is part of the response.
        return jobMapper.toResponse(jobRepository.saveAndFlush(job));
    }

    /** Idempotent: setting the current status again is accepted and changes nothing. */
    public JobResponse updateStatus(Long id, JobStatusRequest request) {
        Job job = find(id);
        job.setStatus(request.status());
        return jobMapper.toResponse(jobRepository.saveAndFlush(job));
    }

    private Job find(Long id) {
        return jobRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job " + id + " not found"));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
