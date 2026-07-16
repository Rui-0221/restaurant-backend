package org.example.restaurant.common;  // common包：放通用工具类


public class BusinessException extends RuntimeException{

    public BusinessException(String message){
        super(message);
    }
}