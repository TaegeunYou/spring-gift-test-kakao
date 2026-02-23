package gift.cucumber.steps;

import gift.cucumber.CucumberSpringConfiguration;
import gift.cucumber.ScenarioContext;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class GiftStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @조건("{string} 옵션의 재고가 {int}개 있다")
    public void 옵션의_재고가_있다(String optionName, int quantity) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", "테스트카테고리");
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE name = ?", Long.class, "테스트카테고리");
        jdbcTemplate.update("INSERT INTO product (name, price, image_url, category_id) VALUES (?, ?, ?, ?)",
                "테스트상품", 5000, "http://img.com/test.jpg", categoryId);
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ? AND category_id = ?", Long.class, "테스트상품", categoryId);
        jdbcTemplate.update("INSERT INTO option (name, quantity, product_id) VALUES (?, ?, ?)",
                optionName, quantity, productId);
        Long optionId = jdbcTemplate.queryForObject(
                "SELECT id FROM option WHERE name = ? AND product_id = ?", Long.class, optionName, productId);
        context.set("optionId:" + optionName, optionId);
    }

    @조건("보내는 회원과 받는 회원이 있다")
    public void 보내는_회원과_받는_회원이_있다() {
        jdbcTemplate.update("INSERT INTO member (name, email) VALUES (?, ?)", "보내는사람", "sender@test.com");
        Long senderId = jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE email = ?", Long.class, "sender@test.com");
        jdbcTemplate.update("INSERT INTO member (name, email) VALUES (?, ?)", "받는사람", "receiver@test.com");
        Long receiverId = jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE email = ?", Long.class, "receiver@test.com");
        context.set("senderId", senderId);
        context.set("receiverId", receiverId);
    }

    @만일("보내는 회원이 {string} {int}개를 선물한다")
    public void 선물한다(String optionName, int quantity) {
        Long senderId = context.get("senderId", Long.class);
        Long receiverId = context.get("receiverId", Long.class);
        Long optionId = context.get("optionId:" + optionName, Long.class);
        Response response = given()
                .contentType(ContentType.JSON)
                .header("Member-Id", senderId)
                .body(Map.of(
                        "optionId", optionId,
                        "quantity", quantity,
                        "receiverId", receiverId,
                        "message", "선물입니다"))
        .when()
                .post("/api/gifts")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @만일("존재하지 않는 옵션으로 선물한다")
    public void 존재하지_않는_옵션으로_선물한다() {
        Long senderId = context.get("senderId", Long.class);
        Long receiverId = context.get("receiverId", Long.class);
        Response response = given()
                .contentType(ContentType.JSON)
                .header("Member-Id", senderId)
                .body(Map.of(
                        "optionId", 9999,
                        "quantity", 1,
                        "receiverId", receiverId,
                        "message", "선물입니다"))
        .when()
                .post("/api/gifts")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @그러면("선물 발송이 성공한다")
    public void 선물_발송이_성공한다() {
        context.getLastResponse().then().statusCode(200);
    }

    @그러면("재고 부족으로 실패한다")
    public void 재고_부족으로_실패한다() {
        context.getLastResponse().then().statusCode(500);
    }

    @그러면("선물 발송이 실패한다")
    public void 선물_발송이_실패한다() {
        context.getLastResponse().then().statusCode(500);
    }
}
