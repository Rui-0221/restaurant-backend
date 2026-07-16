package org.example.restaurant.service;

import org.example.restaurant.entity.OrderDetail;

import java.util.List;

public interface OrderDetailService {
    List<OrderDetail> list();
    OrderDetail getById(Long id);
    void add(OrderDetail orderDetail);
    void update(OrderDetail orderDetail);
    void deleteById(Long id);
}
