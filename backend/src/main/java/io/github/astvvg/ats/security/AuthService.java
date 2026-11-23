package io.github.astvvg.ats.security;

import io.github.astvvg.ats.common.ResourceNotFoundException;
import io.github.astvvg.ats.security.dto.LoginRequest;
import io.github.astvvg.ats.security.dto.LoginResponse;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.dto.UserResponse;
import io.github.astvvg.ats.user.repository.UserRepository;
import io.github.astvvg.ats.user.service.UserMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /** Any failure surfaces as an AuthenticationException, answered with a generic 401. */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                User.normalizeEmail(request.email()), request.password()));
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user =
                userRepository
                        .findById(principal.getId())
                        .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        return LoginResponse.bearer(jwtService.generateToken(user), jwtService.expirationSeconds());
    }

    public UserResponse currentUser(Long id) {
        return userRepository
                .findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
