package com.commercehub.order.api;

import com.commercehub.order.domain.CartItemNotFoundException;
import com.commercehub.order.domain.CartConcurrentModificationException;
import com.commercehub.order.domain.CatalogIntegrationException;
import com.commercehub.order.domain.InvalidCartQuantityException;
import com.commercehub.order.domain.ProductUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CartExceptionHandler {

    @ExceptionHandler(ProductUnavailableException.class)
    ResponseEntity<ProblemDetail> product(
            ProductUnavailableException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "product-not-found", "Producto no disponible",
                exception, request);
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    ResponseEntity<ProblemDetail> item(
            CartItemNotFoundException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "cart-item-not-found", "Artículo no encontrado",
                exception, request);
    }

    @ExceptionHandler(InvalidCartQuantityException.class)
    ResponseEntity<ProblemDetail> quantity(
            InvalidCartQuantityException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-cart-quantity", "Cantidad inválida",
                exception, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Solicitud inválida",
                new IllegalArgumentException("La solicitud contiene datos inválidos."), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(HttpServletRequest request) {
        IllegalArgumentException exception = new IllegalArgumentException(
                "Revisa los campos requeridos y la cantidad mínima de 1.");
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Solicitud inválida",
                exception, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> unreadableRequest(HttpServletRequest request) {
        IllegalArgumentException exception = new IllegalArgumentException(
                "El cuerpo o los parámetros de la solicitud no tienen un formato válido.");
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Solicitud inválida",
                exception, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> concurrentCartChange(HttpServletRequest request) {
        IllegalStateException exception = new IllegalStateException(
                "El carrito cambió durante la operación. Vuelve a intentarlo.");
        return problem(HttpStatus.CONFLICT, "cart-conflict", "Conflicto de carrito",
                exception, request);
    }

    @ExceptionHandler(CartConcurrentModificationException.class)
    ResponseEntity<ProblemDetail> cartChanged(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "cart-conflict", "Conflicto de carrito",
                new IllegalStateException("El carrito cambió durante la operación. Vuelve a intentarlo."),
                request);
    }

    @ExceptionHandler(CatalogIntegrationException.class)
    ResponseEntity<ProblemDetail> catalog(
            CatalogIntegrationException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "catalog-unavailable",
                "Catálogo no disponible",
                new IllegalStateException("No se pudo validar el producto con el catálogo."), request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String type,
            String title,
            Exception exception,
            HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                status, exception.getMessage());
        detail.setTitle(title);
        detail.setType(URI.create("https://commercehub.local/errors/" + type));
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC));

        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null && !correlationId.isBlank()) {
            detail.setProperty("correlation_id", correlationId);
        }

        return ResponseEntity.status(status).body(detail);
    }
}
