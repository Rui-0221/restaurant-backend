package org.example.restaurant.interceptor;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.common.ResponseUtil;
import org.example.restaurant.common.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


@Component  //把这个类交给Spring管理，之后使用不需要new,最通用的注解，没有明确的业务语义
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception{
        //1,放行 OPTIONS 预检请求
        //浏览器在跨域请求前会先发一个OPTIONS 询问服务器允许的方法，必须放行，否则后续请求会失败
        if("OPTIONS".equalsIgnoreCase(request.getMethod())){
            return true;
        }

        //2,从Header中获取token
        //推荐用标准的Authorization:Bearer<token>格式
        String authHeader=request.getHeader("Authorization");

        //3,检查token是否存在且格式正确
        if(authHeader==null||!authHeader.startsWith("Bearer ")){
            ResponseUtil.write401(response,"缺少token或格式错误");
            return false;
        }

        //4,提取真正的token（去掉"Bearer"前缀）
        String token=authHeader.substring(7);
        try{
            //校验token类型：员工拦截器只接受employee类型的token，拒绝user token越权访问
            String tokenType = JwtUtil.parseTokenType(token);
            if (!"employee".equals(tokenType)) {
                ResponseUtil.write401(response, "权限不足，需要员工身份");
                return false;
            }

            //调用JwtUtil解析token，拿到员工ID和角色
            Long employeeId= JwtUtil.parseUserId(token);
            Integer role = JwtUtil.parseRole(token);

            //把员工ID存入request属性中，后面Controller可以通过request.getAttribute("employeeId")直接取
            request.setAttribute("employeeId",employeeId);
            request.setAttribute("role", role);

            //存入ThreadLocal，Service层可直接获取
            UserContext.setEmployeeId(employeeId);
            UserContext.setRole(role);

            return true;//解析成功，放行
        }catch(Exception e){
            //解析失败（token过期/伪造/损坏），返回401
            ResponseUtil.write401(response,"token无效或已过期");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

}
