package org.example.restaurant.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.common.ResponseUtil;
import org.example.restaurant.common.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserJwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception{

        //1,放行OPTIONS预检请求
        if("OPTIONS".equalsIgnoreCase(request.getMethod())){
            return true;
        }

        //2,获取token
        String authHeader=request.getHeader("Authorization");
        if(authHeader==null || !authHeader.startsWith("Bearer ")){
            ResponseUtil.write401(response,"请先登录");
            return false;
        }

        //3,解析token
        String token =authHeader.substring(7);
        try {
            String tokenType = JwtUtil.parseTokenType(token);
            String path = request.getRequestURI();
            boolean sharedPath = path.startsWith("/orders/scan-order")
                    || path.startsWith("/orders/table/");

            if (sharedPath) {
                // 扫码点餐和桌台活跃订单查询：员工和用户均可访问
                if ("employee".equals(tokenType)) {
                    Long employeeId = JwtUtil.parseUserId(token);
                    Integer role = JwtUtil.parseRole(token);
                    request.setAttribute("employeeId", employeeId);
                    request.setAttribute("role", role);
                    UserContext.setEmployeeId(employeeId);
                    UserContext.setRole(role);
                    return true;
                } else if ("user".equals(tokenType)) {
                    Long userId = JwtUtil.parseUserId(token);
                    request.setAttribute("userId", userId);
                    UserContext.setUserId(userId);
                    return true;
                } else {
                    ResponseUtil.write401(response, "权限不足");
                    return false;
                }
            }

            // /users/** 路径仅允许用户身份
            if (!"user".equals(tokenType)) {
                ResponseUtil.write401(response, "权限不足，需要用户身份");
                return false;
            }

            //获取用户ID
            Long userId = JwtUtil.parseUserId(token);
            request.setAttribute("userId", userId);
            UserContext.setUserId(userId);
            return true;
        }catch (Exception e){
            ResponseUtil.write401(response,"token无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
