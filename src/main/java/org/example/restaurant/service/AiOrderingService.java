package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiOrderingResponse;

public interface AiOrderingService {
    AiOrderingResponse chat(AiOrderingRequest request);

    AiOrderConfirmResponse confirm(AiOrderConfirmRequest request);
}
