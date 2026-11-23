package io.github.astvvg.ats.user.service;

import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
