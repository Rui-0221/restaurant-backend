package org.example.restaurant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Dish;
import org.example.restaurant.mapper.DishMapper;
import org.example.restaurant.service.DishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class DishServiceImpl implements DishService {

    private static final Logger log = LoggerFactory.getLogger(DishServiceImpl.class);

    private static final String CACHE_KEY_ON_SALE = "dish:onSale"; // ← Redis 里的 Key 名
    private static final Duration CACHE_TTL = Duration.ofHours(1); // ← 缓存过期时间 1 小时 
    private static final Duration EMPTY_CACHE_TTL = Duration.ofSeconds(60); // ← 缓存空值过期时间 60 秒
    /** 缓存空值标记（与合法的JSON序列化结果区分，避免歧义） */
    private static final String EMPTY_MARKER = "__EMPTY__"; // ← 缓存空值标记

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 禁用时间戳格式，改用 ISO 字符串（如 2026-07-16T16:27:00）

    @Override
    public List<Dish> list() {
        return dishMapper.findAll();
    }

    @Override
    public Dish getById(Long id) {
        Dish dish = dishMapper.findById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在:id=" + id);
        }
        return dish;
    }

    @Override
    public void add(Dish dish) {
        LocalDateTime now = LocalDateTime.now();
        dish.setCreateTime(now);
        dish.setUpdateTime(now);
        dishMapper.insert(dish);
        // 新增菜品后清除缓存，下次查询时重新加载
        evictOnSaleCache();
    }

    @Override
    public void update(Dish dish) {
        dish.setUpdateTime(LocalDateTime.now());
        dishMapper.update(dish);
        // 修改菜品后清除缓存（可能改了status或价格）
        evictOnSaleCache();
    }

    @Override
    public void delete(Long id) {
        dishMapper.deleteById(id);
        // 删除菜品后清除缓存
        evictOnSaleCache();
    }

    // ==================== Redis 缓存：在售菜品（规划 Day8）====================

    /**
     * 查询在售菜品 — Cache-Aside 模式
     * 1. 先查 Redis 缓存
     * 2. 缓存命中 → 直接返回
     * 3. 缓存未命中 → 查数据库 → 写入缓存 → 返回
     *
     * 缓存穿透防护：数据库无数据时缓存空列表60秒，避免大量请求穿透到DB
     */
    @Override
    public List<Dish> listOnSale() {
        // 1. 查缓存（Redis 不可用时降级直查数据库）
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY_ON_SALE);
            if (cached != null) {
                // 缓存命中（包括缓存的空列表）
                return deserializeDishList(cached);
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，降级直查数据库", e);
        }

        // 2. 查数据库
        List<Dish> dishes = dishMapper.findOnSale();

        // 3. 写入缓存（Redis 不可用时跳过，不影响业务）
        if (dishes == null || dishes.isEmpty()) {
            // 缓存空列表短时间，防止缓存穿透
            try {
                redisTemplate.opsForValue().set(CACHE_KEY_ON_SALE, EMPTY_MARKER, EMPTY_CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis 写入空值缓存失败，跳过", e);
            }
            return Collections.emptyList();
        } else {
            try {
                String json = serializeDishList(dishes);
                redisTemplate.opsForValue().set(CACHE_KEY_ON_SALE, json, CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis 写入缓存失败，跳过", e);
            }
            return dishes;
        }
    }

    /**
     * 清除在售菜品缓存
     * 在 add / update / delete 菜品时调用
     */
    private void evictOnSaleCache() {
        try {
            redisTemplate.delete(CACHE_KEY_ON_SALE);
        } catch (Exception e) {
            log.warn("Redis 清除缓存失败，跳过", e);
        }
    }

    // ==================== JSON 序列化/反序列化工具 ====================

    private String serializeDishList(List<Dish> dishes) {
        try {
            return objectMapper.writeValueAsString(dishes);
        } catch (JsonProcessingException e) {
            throw new BusinessException("菜品列表序列化失败");
        }
    }

    private List<Dish> deserializeDishList(String json) {
        // 空值标记 → 返回空列表
        if (EMPTY_MARKER.equals(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Dish>>() {});
        } catch (JsonProcessingException e) {
            // JSON损坏时清除缓存，降级查库
            evictOnSaleCache();
            return dishMapper.findOnSale();
        }
    }
}
