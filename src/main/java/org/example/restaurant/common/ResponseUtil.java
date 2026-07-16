package org.example.restaurant.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 响应工具类 — 提取拦截器中重复的 JSON 响应写入逻辑
 */
public class ResponseUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 写入 401 JSON 响应
     */
    public static void write401(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> result = Result.error(msg);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
    }
}
