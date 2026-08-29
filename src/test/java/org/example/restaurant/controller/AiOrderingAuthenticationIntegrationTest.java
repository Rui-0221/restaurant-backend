package org.example.restaurant.controller;

import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.service.AiOrderingService;
import org.example.restaurant.service.DishAiProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AiOrderingAuthenticationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiOrderingService aiOrderingService;

    @MockBean
    private DishAiProfileService profileService;

    @Test
    void aiOrderingRequiresUserJwtAndRejectsEmployeeJwt() throws Exception {
        String body = "{\"tableId\":3,\"message\":\"推荐一下\"}";

        mockMvc.perform(post("/users/ai-order/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/users/ai-order/chat")
                        .header("Authorization", "Bearer " + JwtUtil.generateToken(1L, 1))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validUserJwtReachesAiOrderingController() throws Exception {
        when(aiOrderingService.chat(any())).thenReturn(new AiOrderingResponse(
                AiOrderAction.ASK_CLARIFICATION, "请告诉我人数",
                AiRecommendationSource.DEEPSEEK, List.of(), BigDecimal.ZERO,
                "conversation-1", null, null));

        mockMvc.perform(post("/users/ai-order/chat")
                        .header("Authorization", "Bearer " + JwtUtil.generateUserToken(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":3,\"message\":\"帮我们点一桌\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.action").value("ASK_CLARIFICATION"));
    }

    @Test
    void profileAdministrationRequiresEmployeeAdminRole() throws Exception {
        mockMvc.perform(get("/admin/dish-ai-profiles")
                        .header("Authorization", "Bearer " + JwtUtil.generateUserToken(7L)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/dish-ai-profiles")
                        .header("Authorization", "Bearer " + JwtUtil.generateToken(2L, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        when(profileService.list()).thenReturn(List.of());
        mockMvc.perform(get("/admin/dish-ai-profiles")
                        .header("Authorization", "Bearer " + JwtUtil.generateToken(1L, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }
}
