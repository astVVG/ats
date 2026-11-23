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
    void databaseIsPostgres16() {
        assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
                .startsWith("PostgreSQL 16");
    }

    @Test
    void flywayHistoryTableExists() {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL",
                        Boolean.class);
        assertThat(exists).isTrue();
    }
}
