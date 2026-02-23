package gift.cucumber;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ScenarioScope
public class ScenarioContext {

    private Response lastResponse;
    private final Map<String, Object> store = new HashMap<>();

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response response) {
        this.lastResponse = response;
    }

    public void set(String key, Object value) {
        store.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) store.get(key);
    }
}
