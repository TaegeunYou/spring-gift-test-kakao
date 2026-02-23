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

public class CategoryStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private ScenarioContext context;

    @조건("{string} 카테고리가 등록되어 있다")
    public void 카테고리가_등록되어_있다(String name) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", name))
        .when()
                .post("/api/categories")
        .then()
                .statusCode(200)
                .extract().response();
        context.set("lastCategoryId", response.jsonPath().getLong("id"));
    }

    @만일("{string} 이름으로 카테고리를 생성한다")
    public void 카테고리를_생성한다(String name) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", name))
        .when()
                .post("/api/categories")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @만일("이름 없이 카테고리를 생성한다")
    public void 이름_없이_카테고리를_생성한다() {
        Response response = given()
                .contentType(ContentType.JSON)
        .when()
                .post("/api/categories")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @만일("카테고리 목록을 조회한다")
    public void 카테고리_목록을_조회한다() {
        Response response = given()
        .when()
                .get("/api/categories")
        .then()
                .extract().response();
        context.setLastResponse(response);
    }

    @그러면("카테고리가 성공적으로 생성된다")
    public void 카테고리가_성공적으로_생성된다() {
        context.getLastResponse().then()
                .statusCode(200)
                .body("id", notNullValue());
    }

    @그리고("카테고리 이름이 {string}이다")
    public void 카테고리_이름이_이다(String name) {
        context.getLastResponse().then()
                .body("name", is(name));
    }

    @그러면("카테고리 목록에 {int}개의 카테고리가 있다")
    public void 카테고리_목록에_N개의_카테고리가_있다(int count) {
        context.getLastResponse().then()
                .statusCode(200)
                .body("size()", is(count));
    }

    @그리고("카테고리 목록에 {string} 카테고리가 포함되어 있다")
    public void 카테고리_목록에_포함되어_있다(String name) {
        context.getLastResponse().then()
                .body("name", hasItem(name));
    }

    @그러면("카테고리 생성이 실패한다")
    public void 카테고리_생성이_실패한다() {
        context.getLastResponse().then()
                .statusCode(400);
    }
}
