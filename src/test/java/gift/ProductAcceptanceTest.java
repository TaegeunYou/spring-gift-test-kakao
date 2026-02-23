package gift;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAcceptanceTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE wish");
        jdbcTemplate.execute("TRUNCATE TABLE option");
        jdbcTemplate.execute("TRUNCATE TABLE product");
        jdbcTemplate.execute("TRUNCATE TABLE category");
        jdbcTemplate.execute("TRUNCATE TABLE member");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    @DisplayName("유효한 정보로 상품을 등록하면 id, name, price, imageUrl이 반환된다")
    void createProduct() {
        Long categoryId = 카테고리를_생성한다("교환권");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "아메리카노",
                        "price", 5000,
                        "imageUrl", "http://img.com/a.jpg",
                        "categoryId", categoryId))
        .when()
                .post("/api/products")
        .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("name", is("아메리카노"))
                .body("price", is(5000))
                .body("imageUrl", is("http://img.com/a.jpg"));
    }

    @Test
    @DisplayName("상품 등록 후 목록 조회 시 해당 상품이 포함된다")
    void createAndRetrieveProduct() {
        Long categoryId = 카테고리를_생성한다("교환권");
        상품을_등록한다("아메리카노", 5000, "http://img.com/a.jpg", categoryId);

        given()
        .when()
                .get("/api/products")
        .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].name", is("아메리카노"));
    }

    @Test
    @DisplayName("여러 상품을 등록하면 모두 목록에 포함된다")
    void createMultipleProductsAndRetrieve() {
        Long categoryId = 카테고리를_생성한다("교환권");
        상품을_등록한다("아메리카노", 5000, "http://img.com/a.jpg", categoryId);
        상품을_등록한다("카페라떼", 6000, "http://img.com/b.jpg", categoryId);

        given()
        .when()
                .get("/api/products")
        .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("name", hasItems("아메리카노", "카페라떼"));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 상품을 등록하면 실패한다")
    void createProductWithInvalidCategory() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "아메리카노",
                        "price", 5000,
                        "imageUrl", "http://img.com/a.jpg",
                        "categoryId", 9999))
        .when()
                .post("/api/products")
        .then()
                .statusCode(500);
    }

    private Long 카테고리를_생성한다(String name) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", name))
        .when()
                .post("/api/categories")
        .then()
                .statusCode(200)
                .extract().response();
        return response.jsonPath().getLong("id");
    }

    private void 상품을_등록한다(String name, int price, String imageUrl, Long categoryId) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", name,
                        "price", price,
                        "imageUrl", imageUrl,
                        "categoryId", categoryId))
        .when()
                .post("/api/products")
        .then()
                .statusCode(200);
    }
}
