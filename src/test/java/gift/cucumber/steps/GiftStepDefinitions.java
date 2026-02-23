package gift.cucumber.steps;

import gift.cucumber.CucumberSpringConfiguration;
import gift.cucumber.ScenarioContext;
import gift.model.Category;
import gift.model.CategoryRepository;
import gift.model.Member;
import gift.model.MemberRepository;
import gift.model.Option;
import gift.model.OptionRepository;
import gift.model.Product;
import gift.model.ProductRepository;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class GiftStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OptionRepository optionRepository;

    @조건("{string} 옵션의 재고가 {int}개 있다")
    public void 옵션의_재고가_있다(String optionName, int quantity) {
        Category category = categoryRepository.save(new Category("테스트카테고리"));
        Product product = productRepository.save(new Product("테스트상품", 5000, "http://img.com/test.jpg", category));
        Option option = optionRepository.save(new Option(optionName, quantity, product));
        context.set("optionId:" + optionName, option.getId());
    }

    @조건("보내는 회원과 받는 회원이 있다")
    public void 보내는_회원과_받는_회원이_있다() {
        Member sender = memberRepository.save(new Member("보내는사람", "sender@test.com"));
        Member receiver = memberRepository.save(new Member("받는사람", "receiver@test.com"));
        context.set("senderId", sender.getId());
        context.set("receiverId", receiver.getId());
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
