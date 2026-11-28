package io.github.astvvg.ats;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.astvvg.ats.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DatabaseContextTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    void contextLoadsWithSchemaValidation() {
        assertThat(ddlAuto).isEqualTo("validate");
    }

    @Test
    void databaseAnswersQueries() {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseIsMysql84() {
        assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
                .startsWith("8.4.");
    }

    @Test
    void sessionsUseUtcAndReadCommitted() {
        assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class))
                .isEqualTo("+00:00");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT @@session.transaction_isolation", String.class))
                .isEqualTo("READ-COMMITTED");
    }

    @Test
    void flywayHistoryTableExists() {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM information_schema.tables"
                                        + " WHERE table_schema = DATABASE()"
                                        + " AND table_name = 'flyway_schema_history'",
                                Integer.class))
                .isEqualTo(1);
    }
}
