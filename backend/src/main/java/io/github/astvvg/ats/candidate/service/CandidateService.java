package io.github.astvvg.ats.candidate.service;

import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.dto.CandidateRequest;
import io.github.astvvg.ats.candidate.dto.CandidateResponse;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.common.PageRequests;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CandidateService {

    static final String EMAIL_TAKEN = "A candidate with this email already exists";
    static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "fullName", "email");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    public static final long MAX_RESUME_BYTES = 5L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String EMAIL_CONSTRAINT = "uq_candidates_email";

    private final CandidateRepository candidateRepository;
    private final CandidateMapper candidateMapper;
    private final ResumeStorage resumeStorage;

    public CandidateService(
            CandidateRepository candidateRepository,
            CandidateMapper candidateMapper,
            ResumeStorage resumeStorage) {
        this.candidateRepository = candidateRepository;
        this.candidateMapper = candidateMapper;
        this.resumeStorage = resumeStorage;
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateResponse> list(String q, Pageable pageable) {
        Pageable sorted = PageRequests.withSort(pageable, SORTABLE_PROPERTIES, DEFAULT_SORT);
        return PageResponse.from(
                candidateRepository
                        .findAll(CandidateRepository.matching(q), sorted)
                        .map(candidateMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(Long id) {
        return candidateMapper.toResponse(find(id));
    }

    public CandidateResponse create(CandidateRequest request) {
        if (candidateRepository.existsByEmail(request.email())) {
            throw new BusinessConflictException(EMAIL_TAKEN);
        }
        Candidate candidate =
                new Candidate(
                        request.fullName().trim(),
                        request.email(),
                        trimToNull(request.phone()),
                        trimToNull(request.source()));
        return candidateMapper.toResponse(saveAndFlush(candidate));
    }

    /** Replaces name, email, phone and source; keeping the own email is not a conflict. */
    public CandidateResponse update(Long id, CandidateRequest request) {
        Candidate candidate = find(id);
        if (candidateRepository.existsByEmailForOtherCandidate(request.email(), id)) {
            throw new BusinessConflictException(EMAIL_TAKEN);
        }
        candidate.update(
                request.fullName().trim(),
                request.email(),
                trimToNull(request.phone()),
                trimToNull(request.source()));
        return candidateMapper.toResponse(saveAndFlush(candidate));
    }

    // Applications do not exist yet; the "has applications" check arrives with Stage 3.
    public void delete(Long id) {
        Candidate candidate = find(id);
        candidateRepository.delete(candidate);
        candidateRepository.flush();
        resumeStorage.deleteCandidateFolder(id);
    }

    /** Stores a new resume and removes the previous one. */
    public CandidateResponse uploadResume(Long id, MultipartFile file) {
        Candidate candidate = find(id);
        byte[] content = readValidPdf(file);
        String previous = candidate.getResumePath();
        String stored = resumeStorage.store(id, content);
        candidate.setResumePath(stored);
        try {
            candidateRepository.saveAndFlush(candidate);
        } catch (RuntimeException ex) {
            resumeStorage.delete(id, stored);
            throw ex;
        }
        if (previous != null) {
            resumeStorage.delete(id, previous);
        }
        return candidateMapper.toResponse(candidate);
    }

    @Transactional(readOnly = true)
    public Path findResume(Long id) {
        Candidate candidate = find(id);
        if (candidate.getResumePath() == null) {
            throw new ResourceNotFoundException("Candidate " + id + " has no resume");
        }
        return resumeStorage
                .find(id, candidate.getResumePath())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Candidate " + id + " has no resume"));
    }

    // The client's file name and Content-Type are ignored: only the bytes are trusted.
    private static byte[] readValidPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badFile("The file is empty");
        }
        if (file.getSize() > MAX_RESUME_BYTES) {
            throw badFile("The file exceeds the maximum size of 5 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read uploaded file", ex);
        }
        if (content.length < PDF_SIGNATURE.length
                || !Arrays.equals(
                        content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
            throw badFile("The file is not a PDF");
        }
        return content;
    }

    private static ResponseStatusException badFile(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    private Candidate saveAndFlush(Candidate candidate) {
        try {
            return candidateRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request may take the same email between the check and the write.
            if (violatesEmailConstraint(ex)) {
                throw new BusinessConflictException(EMAIL_TAKEN);
            }
            throw ex;
        }
    }

    private Candidate find(Long id) {
        return candidateRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate " + id + " not found"));
    }

    private static boolean violatesEmailConstraint(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
