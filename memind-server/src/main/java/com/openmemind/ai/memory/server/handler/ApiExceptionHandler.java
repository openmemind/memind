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

import com.openmemind.ai.memory.server.configuration.RequestIdFilter;
import com.openmemind.ai.memory.server.domain.common.ApiError;
import com.openmemind.ai.memory.server.domain.common.ApiErrorCode;
import com.openmemind.ai.memory.server.domain.common.ErrorResult;
import com.openmemind.ai.memory.server.domain.common.ValidationErrorDetails;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MemoryRuntimeUnavailableException.class)
    public ResponseEntity<ErrorResult<Void>> handleServiceUnavailable(
            MemoryRuntimeUnavailableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.RUNTIME_UNAVAILABLE,
                exception.getMessage(),
                null,
                request,
                exception);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResult<Void>> handleConflict(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                ApiErrorCode.VERSION_CONFLICT,
                exception.getMessage(),
                null,
                request,
                exception);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResult<ValidationErrorDetails>> handleValidationFailure(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                validationDetails(exception),
                request,
                exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResult<Void>> handleMalformedJson(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.MALFORMED_JSON,
                "Malformed JSON request body",
                null,
                request,
                exception);
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResult<Void>> handleBadRequest(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                exception.getMessage(),
                null,
                request,
                exception);
    }

    @ExceptionHandler({
        NoSuchElementException.class,
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ErrorResult<Void>> handleNotFound(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOT_FOUND,
                exception.getMessage(),
                null,
                request,
                exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResult<Void>> handleInternalError(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "Internal server error",
                null,
                request,
                exception);
    }

    private <T> ResponseEntity<ErrorResult<T>> response(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            T details,
            HttpServletRequest request,
            Exception exception) {
        String requestId = resolveRequestId(request);
        logException(status, code.value(), request, requestId, exception);
        return ResponseEntity.status(status)
                .body(new ErrorResult<>(new ApiError<>(code, message, details)));
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(RequestIdFilter.HEADER);
        }
        requestId = RequestIdFilter.sanitizeRequestId(requestId);
        if (requestId == null || requestId.isBlank()) {
            return "-";
        }
        return requestId;
    }

    private void logException(
            HttpStatus status,
            String code,
            HttpServletRequest request,
            String requestId,
            Exception exception) {
        String requestSummary = request.getMethod() + " " + request.getRequestURI();
        if (status.is5xxServerError()) {
            if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                log.warn(
                        "Request failed: status={}, code={}, request={}, requestId={}, message={}",
                        status.value(),
                        code,
                        requestSummary,
                        requestId,
                        exception.getMessage());
                return;
            }
            log.error(
                    "Request failed: status={}, code={}, request={}, requestId={}, message={}",
                    status.value(),
                    code,
                    requestSummary,
                    requestId,
                    exception.getMessage(),
                    exception);
            return;
        }
        log.warn(
                "Request failed: status={}, code={}, request={}, requestId={}, message={}",
                status.value(),
                code,
                requestSummary,
                requestId,
                exception.getMessage());
    }

    private static ValidationErrorDetails validationDetails(Exception exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException validationException) {
            for (FieldError fieldError : validationException.getBindingResult().getFieldErrors()) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            return new ValidationErrorDetails(fieldErrors);
        }
        if (exception instanceof ConstraintViolationException validationException) {
            for (ConstraintViolation<?> violation : validationException.getConstraintViolations()) {
                fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
            }
        }
        return new ValidationErrorDetails(fieldErrors);
    }
}
