package io.github.astvvg.ats.job.service;

import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.dto.JobCreatorResponse;
import io.github.astvvg.ats.job.dto.JobResponse;
import io.github.astvvg.ats.user.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobMapper {

    JobResponse toResponse(Job job);

    JobCreatorResponse toCreator(User user);
}
