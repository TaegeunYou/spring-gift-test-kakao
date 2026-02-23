package gift.cucumber;

import io.cucumber.java.Before;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

public class DataCleanupHook {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before(order = 0)
    public void setUp() {
        RestAssured.port = port;
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE wish");
        jdbcTemplate.execute("TRUNCATE TABLE option");
        jdbcTemplate.execute("TRUNCATE TABLE product");
        jdbcTemplate.execute("TRUNCATE TABLE category");
        jdbcTemplate.execute("TRUNCATE TABLE member");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
