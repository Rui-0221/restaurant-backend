package org.example.restaurant.controller;
import org.example.restaurant.ai.*;
import org.example.restaurant.common.*;
import org.example.restaurant.service.AiOrderingService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AiOrderingControllerTest {
    private final AiOrderingService service = mock(AiOrderingService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiOrderingController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    @BeforeEach void identity() { UserContext.setUserId(7L); }
    @AfterEach void clear() { UserContext.clear(); }
    @Test void chatUsesJwtIdentityAndAcceptsOnlyMessageContext() throws Exception {
        when(service.chat(any())).thenReturn(AiOrderingResponse.clarification("几位？", "c"));
        mvc.perform(post("/users/ai-order/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":3,\"userId\":999,\"requestId\":\"r1\",\"message\":\"推荐\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        verify(service).chat(new AiOrderingRequest(7L, 3L, null, "r1", "推荐"));
    }
    @Test void cancellationUsesJwtOwner() throws Exception {
        mvc.perform(post("/users/ai-order/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":999,\"requestId\":\"r1\"}")).andExpect(status().isOk());
        verify(service).cancel(7L, "r1");
    }
    @Test void requiresRequestIdAndBoundedMessage() throws Exception {
        mvc.perform(post("/users/ai-order/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":3,\"message\":\"推荐\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void failedRecommendationsCarryStructuredError() throws Exception {
        when(service.chat(any())).thenReturn(AiOrderingResponse.failure(AiOrderErrorCode.CANCELLED, "c"));
        mvc.perform(post("/users/ai-order/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":3,\"requestId\":\"r1\",\"message\":\"推荐\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.errorCode").value("CANCELLED"));
    }
    @Test void aiSpecificConfirmationEndpointHasBeenRemoved() throws Exception {
        mvc.perform(post("/users/ai-order/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertInstanceOf(org.springframework.web.servlet.NoHandlerFoundException.class, result.getResolvedException()));
        verifyNoInteractions(service);
    }
}
