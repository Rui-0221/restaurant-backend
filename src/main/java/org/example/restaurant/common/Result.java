package org.example.restaurant.common;  // common包：放通用工具类

import lombok.Data;

@Data  // 自动生成 getter/setter
public class Result<T> {
    // <T> 是泛型：data字段可以是任意类型
    // 查列表时 data 是 List<Category>
    // 查单个时 data 是 Category
    // 增删改时 data 是 String

    private Integer code;  // 1表示成功，0表示失败
    private String msg;    // 提示信息
    private T data;        // 返回的数据，类型由调用方决定

    // 成功时调用这个方法
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 1;
        r.msg = "success";
        r.data = data;
        return r;
    }

    // 失败时调用这个方法
    public static <T> Result<T> error(String msg) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = msg;
        r.data = null;
        return r;
    }
}