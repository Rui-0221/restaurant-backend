package org.example.restaurant.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.net.http.HttpClient;
import static org.junit.jupiter.api.Assertions.*;
class AiClientConfigTest {
    @Test void defaultClientHasABoundedTimeoutAndStartsDisabled() {
        new ApplicationContextRunner().withUserConfiguration(AiClientConfig.class).run(context -> {
            assertNotNull(context.getBean(HttpClient.class));
            var p = context.getBean(AiProperties.class);
            assertFalse(p.isEnabled());
            assertEquals(3, context.getBean(HttpClient.class).connectTimeout().orElseThrow().toSeconds());
            assertEquals(15, p.getReadTimeout().toSeconds());
        });
    }
}
