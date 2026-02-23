package gift.cucumber;

import io.cucumber.java.Before;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

public class DataCleanupHook extends CucumberSpringConfiguration {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Before(order = 0)
    public void setUp() {
        RestAssured.port = port;
        if (isPostgresProfile()) {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE wish, option, product, category, member CASCADE");
        } else {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("TRUNCATE TABLE wish");
            jdbcTemplate.execute("TRUNCATE TABLE option");
            jdbcTemplate.execute("TRUNCATE TABLE product");
            jdbcTemplate.execute("TRUNCATE TABLE category");
            jdbcTemplate.execute("TRUNCATE TABLE member");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private boolean isPostgresProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("cucumber");
    }
}
