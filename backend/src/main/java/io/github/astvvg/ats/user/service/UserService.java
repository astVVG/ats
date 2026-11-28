package io.github.astvvg.ats.user.service;

import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.common.ConstraintViolations;
import io.github.astvvg.ats.common.PageRequests;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.dto.CreateUserRequest;
import io.github.astvvg.ats.user.dto.UpdateUserStatusRequest;
import io.github.astvvg.ats.user.dto.UserResponse;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    static final String EMAIL_TAKEN = "Email already registered";
    static final String SELF_DEACTIVATION = "You cannot deactivate your own account";
    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("id", "name", "email", "role", "active", "createdAt");
    private static final String EMAIL_CONSTRAINT = "uq_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        PageRequests.check(pageable, SORTABLE_PROPERTIES);
        return PageResponse.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    public UserResponse create(CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessConflictException(EMAIL_TAKEN);
        }
        User user =
                new User(
                        request.name().trim(),
                        email,
                        passwordEncoder.encode(request.password()),
                        request.role());
        try {
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request may insert the same email between the check and the insert.
            if (ConstraintViolations.violates(ex, EMAIL_CONSTRAINT)) {
                throw new BusinessConflictException(EMAIL_TAKEN);
            }
            throw ex;
        }
    }

    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request, Long actorId) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User " + id + " not found"));
        boolean active = request.active();
        if (!active && id.equals(actorId)) {
            throw new BusinessConflictException(SELF_DEACTIVATION);
        }
        user.setActive(active);
        return userMapper.toResponse(user);
    }
}
