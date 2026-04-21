/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.server.handler;

import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MemoryRuntimeUnavailableException.class)
    public ResponseEntity<ApiResult<Object>> handleServiceUnavailable(
            MemoryRuntimeUnavailableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "service_unavailable",
                exception.getMessage(),
                null,
                request,
                exception);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResult<Object>> handleConflict(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT, "conflict", exception.getMessage(), null, request, exception);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ApiResult<Object>> handleBadRequest(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "bad_request",
                exception.getMessage(),
                validationDetails(exception),
                request,
                exception);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Object>> handleNotFound(
            NoSuchElementException exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "not_found",
                exception.getMessage(),
                null,
                request,
                exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> handleInternalError(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Internal server error",
                null,
                request,
                exception);
    }

    private ResponseEntity<ApiResult<Object>> response(
            HttpStatus status,
            String code,
            String message,
            Object details,
            HttpServletRequest request,
            Exception exception) {
        String traceId = resolveTraceId(request);
        logException(status, code, request, traceId, exception);
        return ResponseEntity.status(status)
                .body(ApiResult.failure(code, message, details, traceId));
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private void logException(
            HttpStatus status,
            String code,
            HttpServletRequest request,
            String traceId,
            Exception exception) {
        String requestSummary = request.getMethod() + " " + request.getRequestURI();
        if (status.is5xxServerError()) {
            if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                log.warn(
                        "Request failed: status={}, code={}, request={}, traceId={}, message={}",
                        status.value(),
                        code,
                        requestSummary,
                        traceId,
                        exception.getMessage());
                return;
            }
            log.error(
                    "Request failed: status={}, code={}, request={}, traceId={}, message={}",
                    status.value(),
                    code,
                    requestSummary,
                    traceId,
                    exception.getMessage(),
                    exception);
            return;
        }
        log.warn(
                "Request failed: status={}, code={}, request={}, traceId={}, message={}",
                status.value(),
                code,
                requestSummary,
                traceId,
                exception.getMessage());
    }

    private static Object validationDetails(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            for (FieldError fieldError : validationException.getBindingResult().getFieldErrors()) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            return Map.of("fieldErrors", fieldErrors);
        }
        return null;
    }
}
