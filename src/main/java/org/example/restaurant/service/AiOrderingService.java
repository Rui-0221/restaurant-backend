package org.example.restaurant.service;
import org.example.restaurant.ai.*;
public interface AiOrderingService {
    AiOrderingResponse chat(AiOrderingRequest request);
    void cancel(Long userId, String requestId);
    long mealVersion(Long tableId);
}
