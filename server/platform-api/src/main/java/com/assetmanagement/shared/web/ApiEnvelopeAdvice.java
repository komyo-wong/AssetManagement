package com.assetmanagement.shared.web;

import com.assetmanagement.security.TraceIdFilter;
import com.assetmanagement.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.assetmanagement")
public class ApiEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        Class<?> type = returnType.getParameterType();
        // 文件下载等原始响应不要包进 ApiResponse
        if (ApiResponse.class.isAssignableFrom(type)
                || ResponseEntity.class.isAssignableFrom(type)
                || Resource.class.isAssignableFrom(type)
                || type == String.class
                || type == byte[].class) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?> || body instanceof Resource) {
            return body;
        }
        String traceId = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest nativeRequest = servletRequest.getServletRequest();
            traceId = TraceIdFilter.traceId(nativeRequest);
        }
        return ApiResponse.success(body, traceId);
    }
}
