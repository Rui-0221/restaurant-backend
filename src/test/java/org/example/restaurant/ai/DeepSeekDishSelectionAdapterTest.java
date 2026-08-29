package org.example.restaurant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.config.AiProperties;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DeepSeekDishSelectionAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsNonThinkingJsonRequestAndParsesStrictSelection() {
        AiProperties properties = enabledProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekDishSelectionAdapter adapter =
                new DeepSeekDishSelectionAdapter(builder.build(), objectMapper, properties);
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().string(allOf(
                        containsString("\"model\":\"deepseek-v4-flash\""),
                        containsString("\"stream\":false"),
                        containsString("\"thinking\":{\"type\":\"disabled\"}"),
                        containsString("\"response_format\":{\"type\":\"json_object\"}"),
                        containsString("JSON"),
                        containsString("OFF_TOPIC"),
                        containsString("MODEL_IDENTITY"),
                        containsString("不回答"),
                        containsString("\\\"intent\\\":\\\"RECOMMENDATION\\\""),
                        containsString("\\\"items\\\""),
                        containsString("想吃川菜"),
                        containsString("\\\"dishId\\\":101"))))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"content":"{\\"intent\\":\\"RECOMMENDATION\\",\\"items\\":[{\\"dishId\\":101,\\"amount\\":2,\\"reason\\":\\"下饭\\"}],\\"reply\\":\\"选好了\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DishSelectionResult result = adapter.select(
                new DishSelectionRequest("想吃川菜", List.of(catalogDish())));

        assertEquals(DishSelectionIntent.RECOMMENDATION, result.intent());
        assertEquals("选好了", result.reply());
        assertEquals(101L, result.items().get(0).dishId());
        assertEquals(2, result.items().get(0).amount());
        server.verify();
    }

    @Test
    void rejectsTruncatedResponse() {
        assertProviderFailure("""
                {"choices":[{"finish_reason":"length","message":{"content":"{\\"items\\":[]}"}}]}
                """, MediaType.APPLICATION_JSON);
    }

    @Test
    void rejectsEmptyResponseContent() {
        assertProviderFailure("""
                {"choices":[{"finish_reason":"stop","message":{"content":""}}]}
                """, MediaType.APPLICATION_JSON);
    }

    @Test
    void rejectsMalformedJsonContent() {
        assertProviderFailure("""
                {"choices":[{"finish_reason":"stop","message":{"content":"not-json"}}]}
                """, MediaType.APPLICATION_JSON);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coerciveOrTrailingContent")
    void rejectsCoerciveValuesAndTrailingJsonTokens(String scenario, String content) throws Exception {
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", "stop",
                        "message", Map.of("content", content)))));

        assertProviderFailure(responseBody, MediaType.APPLICATION_JSON);
        assertEquals(2, objectMapper.readValue("\"2\"", Integer.class),
                "adapter 的严格配置不得污染共享 ObjectMapper");
    }

    private static Stream<Arguments> coerciveOrTrailingContent() {
        return Stream.of(
                Arguments.of("amount浮点数", """
                        {"items":[{"dishId":101,"amount":1.5,"reason":"下饭"}],"reply":"选好了"}
                        """),
                Arguments.of("amount数字字符串", """
                        {"items":[{"dishId":101,"amount":"2","reason":"下饭"}],"reply":"选好了"}
                        """),
                Arguments.of("dishId数字字符串", """
                        {"items":[{"dishId":"101","amount":2,"reason":"下饭"}],"reply":"选好了"}
                        """),
                Arguments.of("dishId布尔值", """
                        {"items":[{"dishId":true,"amount":2,"reason":"下饭"}],"reply":"选好了"}
                        """),
                Arguments.of("尾随JSON对象", """
                        {"items":[{"dishId":101,"amount":2,"reason":"下饭"}],"reply":"选好了"}
                        {"items":[],"reply":"覆盖前一结果"}
                        """)
        );
    }

    @Test
    void rejectsRateLimitedResponse() {
        AiProperties properties = enabledProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekDishSelectionAdapter adapter =
                new DeepSeekDishSelectionAdapter(builder.build(), objectMapper, properties);
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(AiProviderException.class, () -> adapter.select(
                new DishSelectionRequest("想吃川菜", List.of(catalogDish()))));
        server.verify();
    }

    @Test
    void disabledOrMissingKeyFailsBeforeNetworkCall() {
        AiProperties properties = enabledProperties();
        properties.setApiKey(" ");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekDishSelectionAdapter adapter =
                new DeepSeekDishSelectionAdapter(builder.build(), objectMapper, properties);

        assertThrows(AiProviderException.class, () -> adapter.select(
                new DishSelectionRequest("想吃川菜", List.of(catalogDish()))));
        server.verify();
    }

    private void assertProviderFailure(String responseBody, MediaType mediaType) {
        AiProperties properties = enabledProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekDishSelectionAdapter adapter =
                new DeepSeekDishSelectionAdapter(builder.build(), objectMapper, properties);
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(responseBody, mediaType));

        assertThrows(AiProviderException.class, () -> adapter.select(
                new DishSelectionRequest("想吃川菜", List.of(catalogDish()))));
        server.verify();
    }

    private AiProperties enabledProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey("test-key");
        properties.setModel("deepseek-v4-flash");
        return properties;
    }

    private DishAiCatalogItem catalogDish() {
        DishAiCatalogItem item = new DishAiCatalogItem();
        item.setDishId(101L);
        item.setDishName("宫保鸡丁");
        item.setPrice(new BigDecimal("38.00"));
        item.setCuisine("川菜");
        item.setTasteTags("鲜香,微辣");
        item.setSpicyLevel(2);
        item.setIngredients("鸡肉,花生");
        item.setAllergens("花生");
        item.setDietaryTags("含肉");
        item.setRecommendationNotes("经典川味");
        item.setServingPeople(2);
        item.setProfileStatus("VERIFIED");
        return item;
    }
}
