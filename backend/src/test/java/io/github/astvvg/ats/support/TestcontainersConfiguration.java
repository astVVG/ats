package io.github.astvvg.ats.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Single container shared by every test context in the run.
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    // Run by the server initialization as root; the test user cannot grant itself.
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("mysql/grant-test-user.sql"),
                            "/docker-entrypoint-initdb.d/grant-test-user.sql")
                    // A non UTC server zone proves that the sessions force UTC themselves.
                    .withCommand(
                            "--default-time-zone=-06:00", "--collation-server=utf8mb4_0900_as_ci");

    static {
        MYSQL.start();
    }

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return MYSQL;
    }
}
