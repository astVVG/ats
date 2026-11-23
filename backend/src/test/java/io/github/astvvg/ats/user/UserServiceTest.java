package io.github.astvvg.ats.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.astvvg.ats.common.BusinessConflictException;
import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.dto.CreateUserRequest;
import io.github.astvvg.ats.user.dto.UpdateUserStatusRequest;
import io.github.astvvg.ats.user.dto.UserResponse;
import io.github.astvvg.ats.user.repository.UserRepository;
import io.github.astvvg.ats.user.service.UserMapper;
import io.github.astvvg.ats.user.service.UserService;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService =
                new UserService(
                        userRepository, passwordEncoder, Mappers.getMapper(UserMapper.class));
    }

    private static User existingUser(long id, boolean active) {
        User user = new User("Existing", "existing@example.com", "hash", Role.RECRUITER);
        ReflectionTestUtils.setField(user, "id", id);
        user.setActive(active);
        return user;
    }

    @Test
    void createNormalizesEmailAndEncodesPassword() {
        when(userRepository.existsByEmail("rita@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Rita12345!")).thenReturn("$2a$encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response =
                userService.create(
                        new CreateUserRequest(
                                " Rita ", " Rita@Example.COM ", "Rita12345!", Role.RECRUITER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("rita@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Rita");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$encoded");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.RECRUITER);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(response.email()).isEqualTo("rita@example.com");
    }

    @Test
    void createWithDuplicateEmailIsConflict() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                        userService.create(
                                new CreateUserRequest(
                                        "Dup", "TAKEN@example.com", "Password1", Role.ADMIN)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Email already registered");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueConstraintRaceIsConflict() {
        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("$2a$encoded");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate",
                                new ConstraintViolationException(
                                        "duplicate", new SQLException(), "uq_users_email")));

        assertThatThrownBy(() ->
                        userService.create(
                                new CreateUserRequest(
                                        "Race", "race@example.com", "Password1", Role.RECRUITER)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Email already registered");
    }

    @Test
    void updateStatusOfMissingUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> userService.updateStatus(99L, new UpdateUserStatusRequest(false), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void selfDeactivationIsRejected() {
        User admin = existingUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(
                        () -> userService.updateStatus(1L, new UpdateUserStatusRequest(false), 1L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("You cannot deactivate your own account");
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void updateStatusChangesOtherUser() {
        User other = existingUser(2L, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));

        UserResponse response =
                userService.updateStatus(2L, new UpdateUserStatusRequest(false), 1L);

        assertThat(other.isActive()).isFalse();
        assertThat(response.active()).isFalse();
    }

    @Test
    void listRejectsUnsupportedSortProperty() {
        assertThatThrownBy(() -> userService.list(PageRequest.of(0, 20, Sort.by("passwordHash"))))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userRepository, never()).findAll(any(PageRequest.class));
    }
}
