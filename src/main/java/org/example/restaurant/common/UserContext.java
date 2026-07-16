package org.example.restaurant.common;

/**
 * ThreadLocal 工具类 — 存储当前请求的员工/用户ID和角色
 * 拦截器在 preHandle 中设置，Controller/Service 可直接获取
 */
public class UserContext {

    private static final ThreadLocal<Long> EMPLOYEE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setEmployeeId(Long employeeId) {
        EMPLOYEE_ID.set(employeeId);
    }

    public static Long getEmployeeId() {
        return EMPLOYEE_ID.get();
    }

    public static void setRole(Integer role) {
        ROLE.set(role);
    }

    public static Integer getRole() {
        return ROLE.get();
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        EMPLOYEE_ID.remove();
        ROLE.remove();
        USER_ID.remove();
    }
}
