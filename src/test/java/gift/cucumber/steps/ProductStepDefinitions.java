package gift.cucumber.steps;

import gift.cucumber.CucumberSpringConfiguration;
import gift.cucumber.ScenarioContext;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class ProductStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private ScenarioContext context;

    @조건("{string} 카테고리에 {string} 상품이 등록되어 있다")
    public void 카테고리에_상품이_등록되어_있다(String categoryName, String productName) {
        Long categoryId = context.get("lastCategoryId", Long.class);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", productName,
                        "price", 5000,
                        "imageUrl", "http://img.com/default.jpg",
                        "categoryId", categoryId))
        .when()
                .post("/api/products")
        .then()
                .statusCode(200);
    }

    @만일("{string} 카테고리에 {string} 상품을 {int}원에 등록한다")
    public void 카테고리에_상품을_등록한다(String categoryName, String productName, int price) {
        Long categoryId = context.get("lastCategoryId", Long.class);
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", productName,
                        "price", price,
                        "imageUrl", "http://img.com/default.jpg",
                        "categoryId", categoryId))
        .when()
                .post("/api/products")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @만일("존재하지 않는 카테고리에 상품을 등록한다")
    public void 존재하지_않는_카테고리에_상품을_등록한다() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "아메리카노",
                        "price", 5000,
                        "imageUrl", "http://img.com/default.jpg",
                        "categoryId", 9999))
        .when()
                .post("/api/products")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @만일("상품 목록을 조회한다")
    public void 상품_목록을_조회한다() {
        Response response = given()
        .when()
                .get("/api/products")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @그러면("상품이 성공적으로 등록된다")
    public void 상품이_성공적으로_등록된다() {
        context.getLastResponse().then()
                .statusCode(200)
                .body("id", notNullValue());
    }

    @그리고("상품 이름이 {string}이다")
    public void 상품_이름이_이다(String name) {
        context.getLastResponse().then()
                .body("name", is(name));
    }

    @그리고("상품 가격이 {int}원이다")
    public void 상품_가격이_원이다(int price) {
        context.getLastResponse().then()
                .body("price", is(price));
    }

    @그러면("상품 목록에 {int}개의 상품이 있다")
    public void 상품_목록에_N개의_상품이_있다(int count) {
        context.getLastResponse().then()
                .statusCode(200)
                .body("size()", is(count));
    }

    @그리고("상품 목록에 {string} 상품이 포함되어 있다")
    public void 상품_목록에_포함되어_있다(String name) {
        context.getLastResponse().then()
                .body("name", hasItem(name));
    }

    @그러면("상품 등록이 실패한다")
    public void 상품_등록이_실패한다() {
        context.getLastResponse().then()
                .statusCode(500);
    }
}
