package io.github.astvvg.ats.candidate.service;

import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.dto.CandidateResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CandidateMapper {

    @Mapping(target = "hasResume", expression = "java(candidate.getResumePath() != null)")
    CandidateResponse toResponse(Candidate candidate);
}
