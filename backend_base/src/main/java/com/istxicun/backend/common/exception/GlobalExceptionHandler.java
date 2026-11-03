package com.istxicun.backend.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.istxicun.backend.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotLoginException.class)
  ResponseEntity<ApiResponse<Void>> unauthenticated(NotLoginException ex) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
  }

  @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
  ResponseEntity<ApiResponse<Void>> forbidden(Exception ex) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "无访问权限");
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
    return error(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
  }

  @ExceptionHandler({
    BindException.class,
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiResponse<Void>> invalid(Exception ex) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请求参数不合法");
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  ResponseEntity<ApiResponse<Void>> missing(Exception ex) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> method(Exception ex) {
    return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不支持");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
    log.error("Unhandled request failure", ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用");
  }

  private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiResponse.error(code, message));
  }
}
