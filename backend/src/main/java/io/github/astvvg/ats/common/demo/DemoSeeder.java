package io.github.astvvg.ats.common.demo;

import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.validation.MaxUtf8Bytes;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import io.github.astvvg.ats.user.service.AdminSeeder.AdminProperties;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Loads demo data in development. Runs after the admin seeder because the demo needs that user. It
 * never touches business data that already exists.
 */
@Component
@Profile("dev & !test & !prod")
@Order(2)
@EnableConfigurationProperties(DemoSeeder.DemoProperties.class)
public class DemoSeeder implements ApplicationRunner {

    static final String RECRUITER_EMAIL = "recruiter@demo.test";
    private static final String RECRUITER_NAME = "Demo Recruiter";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final DemoProperties properties;
    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;

    public DemoSeeder(
            DemoProperties properties,
            AdminProperties adminProperties,
            UserRepository userRepository,
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.adminProperties = adminProperties;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<User> admin = seedAdmin();
        if (admin.isEmpty()) {
            log.warn("The ADMIN_EMAIL user does not exist; skipping demo data");
            return;
        }
        // Before looking at business data: the recruiter may be configured on a later start.
        User author = recruiter().orElseGet(admin::get);
        if (jobRepository.count() > 0 || candidateRepository.count() > 0) {
            log.info("Jobs or candidates already exist; skipping demo data");
            return;
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(
                status -> new DemoData(jdbcTemplate, author.getId(), now).write());
        log.info(
                "Loaded demo data: {} jobs, {} candidates with one application each",
                DemoData.jobCount(),
                DemoData.applicationCount());
    }

    private Optional<User> seedAdmin() {
        if (!StringUtils.hasText(adminProperties.email())) {
            return Optional.empty();
        }
        return userRepository.findByEmail(User.normalizeEmail(adminProperties.email()));
    }

    /** The demo recruiter, created if possible; an existing one is reused as it is. */
    private Optional<User> recruiter() {
        Optional<User> existing = userRepository.findByEmail(RECRUITER_EMAIL);
        if (existing.isPresent()) {
            return existing;
        }
        if (!StringUtils.hasText(properties.recruiterPassword())) {
            log.warn(
                    "DEMO_RECRUITER_PASSWORD is not set; {} was not created and any demo data"
                            + " belongs to the admin",
                    RECRUITER_EMAIL);
            return Optional.empty();
        }
        User created =
                userRepository.save(
                        new User(
                                RECRUITER_NAME,
                                RECRUITER_EMAIL,
                                passwordEncoder.encode(properties.recruiterPassword()),
                                Role.RECRUITER));
        log.info("Created demo recruiter {}", RECRUITER_EMAIL);
        return Optional.of(created);
    }

    /** Same password rules and startup failure as the admin seeder. */
    @Validated
    @ConfigurationProperties("app.demo")
    public record DemoProperties(String recruiterPassword) {

        static final int MIN_PASSWORD_LENGTH = 8;

        // Validated through a boolean property so the password never appears in startup errors.
        @AssertTrue(
                message =
                        "app.demo.recruiter-password (DEMO_RECRUITER_PASSWORD) must have at least"
                                + " 8 characters")
        public boolean isPasswordLengthValid() {
            return !StringUtils.hasText(recruiterPassword)
                    || recruiterPassword.length() >= MIN_PASSWORD_LENGTH;
        }

        @AssertTrue(
                message =
                        "app.demo.recruiter-password (DEMO_RECRUITER_PASSWORD) must be at most 72"
                                + " bytes in UTF-8 (BCrypt limit)")
        public boolean isPasswordWithinBcryptLimit() {
            return MaxUtf8Bytes.Validator.fits(recruiterPassword, MaxUtf8Bytes.BCRYPT_MAX_BYTES);
        }
    }
}
