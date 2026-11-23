package io.github.astvvg.ats.user.controller;

import io.github.astvvg.ats.common.OpenApiConfig;
import io.github.astvvg.ats.common.PageResponse;
import io.github.astvvg.ats.security.AuthenticatedUser;
import io.github.astvvg.ats.user.dto.CreateUserRequest;
import io.github.astvvg.ats.user.dto.UpdateUserStatusRequest;
import io.github.astvvg.ats.user.dto.UserResponse;
import io.github.astvvg.ats.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User management (ADMIN only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
@ApiResponse(responseCode = "403", description = "Requires the ADMIN role")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(
            summary = "List users",
            description =
                    "Sortable by id, name, email, role, active and createdAt (default createdAt,desc).")
    @ApiResponse(responseCode = "200", description = "Page of users")
    @ApiResponse(responseCode = "400", description = "Unsupported sort property")
    public PageResponse<UserResponse> list(
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return userService.list(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Activate or deactivate a user")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "Cannot deactivate your own account")
    public UserResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.updateStatus(id, request, actor.getId());
    }
}
