package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.OrderDetail;
import org.example.restaurant.mapper.OrderDetailMapper;
import org.example.restaurant.service.OrderDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderDetailServiceImpl implements OrderDetailService {

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Override
    public List<OrderDetail> list(){
        return orderDetailMapper.findAll();
    }

    @Override
    public OrderDetail getById(Long id){
        OrderDetail orderDetail=orderDetailMapper.getById(id);
        if(orderDetail==null){
            throw new BusinessException("订单详情不存在:id="+id);
        }
        return orderDetail;
    }

    @Override
    public void add(OrderDetail orderDetail){
        orderDetailMapper.insert(orderDetail);
    }

    @Override
    public void update(OrderDetail orderDetail){
        orderDetailMapper.update(orderDetail);
    }

    @Override
    public void deleteById(Long id){
        orderDetailMapper.deleteById(id);
    }
}
