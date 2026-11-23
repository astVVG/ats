package io.github.astvvg.ats.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RoleAuthorizationTest.RoleTestController.class})
@Transactional
class RoleAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    @RestController
    @RequestMapping("/api/test-roles")
    static class RoleTestController {

        @GetMapping("/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "admin";
        }

        @GetMapping("/staff")
        @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
        public String staff() {
            return "staff";
        }

        @GetMapping("/authenticated")
        public String authenticated() {
            return "authenticated";
        }
    }

    private String tokenFor(Role role) {
        User user =
                userRepository.saveAndFlush(
                        new User(
                                "Role Test",
                                "role-" + UUID.randomUUID() + "@example.com",
                                "not-used",
                                role));
        return jwtService.generateToken(user);
    }

    private static MockHttpServletRequestBuilder getWithToken(String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void adminCanAccessAdminRoute() throws Exception {
        mockMvc.perform(getWithToken("/api/test-roles/admin", tokenFor(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void recruiterIsForbiddenOnAdminRoute() throws Exception {
        mockMvc.perform(getWithToken("/api/test-roles/admin", tokenFor(Role.RECRUITER)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Access denied"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api/test-roles/admin"));
    }

    @Test
    void anonymousIsUnauthorizedOnAdminRoute() throws Exception {
        mockMvc.perform(get("/api/test-roles/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void bothRolesCanAccessStaffRoute() throws Exception {
        mockMvc.perform(getWithToken("/api/test-roles/staff", tokenFor(Role.ADMIN)))
                .andExpect(status().isOk());
        mockMvc.perform(getWithToken("/api/test-roles/staff", tokenFor(Role.RECRUITER)))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedRouteRequiresOnlyAValidToken() throws Exception {
        mockMvc.perform(getWithToken("/api/test-roles/authenticated", tokenFor(Role.RECRUITER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/test-roles/authenticated")).andExpect(status().isUnauthorized());
    }
}
