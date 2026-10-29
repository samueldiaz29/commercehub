package com.commercehub.order.client;

import com.commercehub.order.domain.CatalogIntegrationException;
import com.commercehub.order.domain.ProductUnavailableException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogClient {

    private final RestClient client;

    public CatalogClient(
            RestClient.Builder builder,
            @Value("${catalog-service.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public CatalogProduct getPublishedProduct(Long id) {
        try {
            CatalogProduct product = client.get()
                    .uri("/api/v1/products/{id}", id)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            throw new ProductUnavailableException(id);
                        }
                        throw new CatalogIntegrationException(
                                "El catálogo rechazó la consulta del producto.", null);
                    })
                    .body(CatalogProduct.class);

            if (product == null) {
                throw invalidCatalogResponse("El catálogo devolvió una respuesta vacía.");
            }
            if (!id.equals(product.id())) {
                throw invalidCatalogResponse("El catálogo devolvió un producto distinto al solicitado.");
            }
            if (!product.published()) {
                throw new ProductUnavailableException(id);
            }
            if (!"PEN".equals(product.currency())) {
                throw invalidCatalogResponse("El producto del catálogo no está expresado en PEN.");
            }
            BigDecimal price = product.price();
            if (price == null
                    || price.signum() <= 0
                    || price.stripTrailingZeros().scale() > 2
                    || price.precision() - price.scale() > 17) {
                throw invalidCatalogResponse("El catálogo devolvió un precio inválido.");
            }
            return product;
        } catch (ProductUnavailableException | CatalogIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CatalogIntegrationException(
                    "No se pudo consultar catalog-service.", exception);
        }
    }

    private CatalogIntegrationException invalidCatalogResponse(String message) {
        return new CatalogIntegrationException(message, null);
    }
}
