package org.example.restaurant.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 改为 Spring 组件后，密钥从配置文件注入（jwt.secret），
 * 过期时间从配置文件注入（jwt.expiration），均有默认值兜底。
 * 保留静态方法以兼容现有调用方，通过 @PostConstruct 初始化静态字段。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:please-change-this-secret-key-in-production-at-least-32-chars}")
    private String secretConfig;

    @Value("${jwt.expiration:7200000}")
    private long expirationConfig;

    private static String SECRET;
    private static long EXPIRATION;

    @PostConstruct
    public void init() {
        SECRET = secretConfig;
        EXPIRATION = expirationConfig;
    }

    /**
     * 新增生成用户专属token得方法
     */
    public static String generateUserToken(Long userId){
        SecretKey key=Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type","user")
                .expiration(new Date(System.currentTimeMillis()+EXPIRATION))
                .signWith(key)
                .compact();
    }


    // 生成员工token的方法（含角色）
    public static String generateToken(Long employeeId, Integer role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(employeeId))
                .claim("type","employee")
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }

    /**
     * 兼容旧版：员工token（无角色信息时默认管理员角色）
     */
    public static String generateToken(Long employeeId) {
        return generateToken(employeeId, 1);
    }

    /**
     * 解析Token，获取用户/员工ID（通用方法）
     * 员工和用户共用这个方法
     */
    public static Long parseUserId(String token){
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parser()
                .verifyWith(key)  // 使用 SecretKey 对象验证
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());  // 获取ID
    }

    /**
     * 解析token类型
     * @return "user"或"employee"
     */
    public static String parseTokenType(String token){
        SecretKey key= Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims=Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("type",String.class);
    }

    /**
     * 从token中解析角色
     * @return 角色值 (1管理员/2服务员/3后厨)，无角色时返回null
     */
    public static Integer parseRole(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", Integer.class);
    }
}