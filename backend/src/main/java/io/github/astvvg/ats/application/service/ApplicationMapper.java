package io.github.astvvg.ats.application.service;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.dto.ApplicationResponse;
import io.github.astvvg.ats.application.dto.BoardCandidateResponse;
import io.github.astvvg.ats.application.dto.BoardCardResponse;
import io.github.astvvg.ats.application.dto.BoardJobResponse;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.job.domain.Job;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicationMapper {

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "candidateId", source = "candidate.id")
    ApplicationResponse toResponse(Application application);

    BoardJobResponse toBoardJob(Job job);

    BoardCardResponse toBoardCard(Application application);

    BoardCandidateResponse toBoardCandidate(Candidate candidate);
}
