package org.example.restaurant.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

//可以理解成：专门处理所有 Controller 里抛出的异常的「总客服」
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //1，处理业务异常：主动抛出的异常（throw）
    @ExceptionHandler(BusinessException.class)
    public Result<String> handlerBusinessException(BusinessException e){
        return Result.error(e.getMessage());
    }

    //2，处理数据库异常：SQL语句错误：查询的表不存在等问题
    @ExceptionHandler(DataAccessException.class)
    public Result<String> handlerDataAccessException(DataAccessException e){
        log.error("数据库操作失败", e);
        return Result.error("数据库操作失败");
    }

    //3，处理参数校验异常：用户输入的参数有错误
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handlerValidationException(MethodArgumentNotValidException e){
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error("参数校验失败: " + errorMsg);
    }

    //4，处理其他异常：系统异常、网络异常等
    @ExceptionHandler(Exception.class)
    public Result<String> handlerException(Exception e){
        log.error("系统异常", e);
        return Result.error("系统异常，请稍后重试");
    }
}
