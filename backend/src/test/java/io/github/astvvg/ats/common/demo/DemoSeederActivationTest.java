package io.github.astvvg.ats.common.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.repository.UserRepository;
import io.github.astvvg.ats.user.service.AdminSeeder;
import io.github.astvvg.ats.user.service.AdminSeeder.AdminProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Which profiles register the demo seeder and how it validates its password. No application-*.yml
 * is loaded here, so the local .env file is never read.
 */
class DemoSeederActivationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminProperties.class)
    static class AdminConfiguration {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AdminConfiguration.class, DemoSeeder.class)
                    .withBean(UserRepository.class, () -> mock(UserRepository.class))
                    .withBean(JobRepository.class, () -> mock(JobRepository.class))
                    .withBean(CandidateRepository.class, () -> mock(CandidateRepository.class))
                    .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                    .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                    .withBean(
                            PlatformTransactionManager.class,
                            () -> mock(PlatformTransactionManager.class));

    @ParameterizedTest
    @ValueSource(
            strings = {
                "spring.profiles.active=dev",
                // How the application starts when no profile is chosen.
                "spring.profiles.default=dev"
            })
    void activeInDev(String profiles) {
        runner.withPropertyValues(profiles)
                .run(context -> assertThat(context).hasSingleBean(DemoSeeder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "prod", "dev,test", "dev,prod"})
    void inactiveWithTestOrProd(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).doesNotHaveBean(DemoSeeder.class));
    }

    @Test
    void runsAfterTheAdminSeeder() {
        assertThat(OrderUtils.getOrder(DemoSeeder.class))
                .isGreaterThan(OrderUtils.getOrder(AdminSeeder.class));
    }

    @Test
    void invalidPasswordsFailStartupWithoutExposingThem() {
        for (String password : new String[] {"short7!", "ñ".repeat(37)}) {
            runner.withPropertyValues(
                            "spring.profiles.active=dev", "app.demo.recruiter-password=" + password)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        StringBuilder messages = new StringBuilder();
                        for (Throwable t = context.getStartupFailure();
                                t != null;
                                t = t.getCause()) {
                            messages.append(t.getMessage());
                        }
                        assertThat(messages.toString())
                                .contains("DEMO_RECRUITER_PASSWORD")
                                .doesNotContain(password);
                    });
        }
    }

    @Test
    void validOrAbsentPasswordStarts() {
        for (String password : new String[] {"", "eight-88", "ñ".repeat(36)}) {
            runner.withPropertyValues(
                            "spring.profiles.active=dev", "app.demo.recruiter-password=" + password)
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }
}
