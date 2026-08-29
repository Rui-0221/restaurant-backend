package org.example.restaurant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiClientConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiClientConfig.class);

    @Test
    void startsDisabledWithoutApiKeyAndBindsSafeDefaults() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean(RestClient.class));
            AiProperties properties = context.getBean(AiProperties.class);
            assertFalse(properties.isEnabled());
            assertEquals("", properties.getApiKey());
            assertEquals("https://api.deepseek.com", properties.getBaseUrl());
            assertEquals("deepseek-v4-flash", properties.getModel());
            assertEquals(3_000L, properties.getConnectTimeout().toMillis());
            assertEquals(15_000L, properties.getReadTimeout().toMillis());
        });
    }
}
