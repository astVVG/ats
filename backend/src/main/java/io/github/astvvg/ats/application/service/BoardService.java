package io.github.astvvg.ats.application.service;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.dto.BoardCardResponse;
import io.github.astvvg.ats.application.dto.BoardColumnResponse;
import io.github.astvvg.ats.application.dto.BoardResponse;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BoardService {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;

    public BoardService(
            JobRepository jobRepository,
            ApplicationRepository applicationRepository,
            ApplicationMapper applicationMapper) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.applicationMapper = applicationMapper;
    }

    /** The job and its applications grouped by stage; closed jobs are included. */
    public BoardResponse get(Long jobId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Job " + jobId + " not found"));

        Map<Stage, List<BoardCardResponse>> cards = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            cards.put(stage, new ArrayList<>());
        }
        // Already ordered by position, so each column keeps that order.
        for (Application application : applicationRepository.findBoardByJobId(jobId)) {
            cards.get(application.getStage()).add(applicationMapper.toBoardCard(application));
        }

        List<BoardColumnResponse> columns =
                Arrays.stream(Stage.values())
                        .map(stage -> new BoardColumnResponse(stage, cards.get(stage)))
                        .toList();
        return new BoardResponse(applicationMapper.toBoardJob(job), columns);
    }
}
