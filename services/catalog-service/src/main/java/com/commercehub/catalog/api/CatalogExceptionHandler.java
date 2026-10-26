package com.commercehub.catalog.api;

import com.commercehub.catalog.application.ProductNotFoundException;
import com.commercehub.catalog.application.CategoryNotFoundException;
import com.commercehub.catalog.application.ProductConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail productNotFound(ProductNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "product-not-found", "Producto no encontrado", exception, request);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ProblemDetail categoryNotFound(CategoryNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "category-not-found", "Categoría no encontrada", exception, request);
    }

    @ExceptionHandler(ProductConflictException.class)
    ProblemDetail productConflict(ProductConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "product-conflict", "Conflicto de producto", exception, request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class})
    ProblemDetail invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Solicitud inválida", exception, request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String title,
                                  Exception exception, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setType(URI.create("https://commercehub.local/errors/" + code));
        detail.setTitle(title);
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("timestamp", Instant.now());
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null && !correlationId.isBlank()) {
            detail.setProperty("correlation_id", correlationId);
        }
        return detail;
    }
}
