package com.commercehub.order.client;

import com.commercehub.order.domain.CatalogIntegrationException;
import com.commercehub.order.domain.ProductUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientTest {

    private MockRestServiceServer server;
    private CatalogClient catalog;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        catalog = new CatalogClient(builder, "http://catalog");
    }

    @Test
    void returnsPublishedPENProduct() {
        server.expect(requestTo("http://catalog/api/v1/products/13"))
                .andRespond(withSuccess("""
                        {"id":13,"price":25.50,"currency":"PEN","published":true}
                        """, MediaType.APPLICATION_JSON));

        CatalogProduct product = catalog.getPublishedProduct(13L);

        assertThat(product.price()).isEqualByComparingTo("25.50");
        server.verify();
    }

    @Test
    void mapsMissingOrUnpublishedProductsToNotFound() {
        server.expect(requestTo("http://catalog/api/v1/products/13"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> catalog.getPublishedProduct(13L))
                .isInstanceOf(ProductUnavailableException.class);
        setUp();

        server.expect(requestTo("http://catalog/api/v1/products/14"))
                .andRespond(withSuccess("""
                        {"id":14,"price":10.00,"currency":"PEN","published":false}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> catalog.getPublishedProduct(14L))
                .isInstanceOf(ProductUnavailableException.class);
        server.verify();
    }

    @Test
    void mapsCatalogHTTPFailuresAndNetworkErrorsToIntegrationFailures() {
        server.expect(requestTo("http://catalog/api/v1/products/13"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> catalog.getPublishedProduct(13L))
                .isInstanceOf(CatalogIntegrationException.class);
        setUp();

        server.expect(requestTo("http://catalog/api/v1/products/14"))
                .andRespond(withException(new IOException("connection refused")));
        assertThatThrownBy(() -> catalog.getPublishedProduct(14L))
                .isInstanceOf(CatalogIntegrationException.class);
        server.verify();
    }

    @Test
    void rejectsEmptyOrInvalidCatalogResponsesAsIntegrationFailures() {
        server.expect(requestTo("http://catalog/api/v1/products/13"))
                .andRespond(withSuccess());
        assertThatThrownBy(() -> catalog.getPublishedProduct(13L))
                .isInstanceOf(CatalogIntegrationException.class);
        setUp();

        server.expect(requestTo("http://catalog/api/v1/products/14"))
                .andRespond(withSuccess("""
                        {"id":14,"price":-1,"currency":"PEN","published":true}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> catalog.getPublishedProduct(14L))
                .isInstanceOf(CatalogIntegrationException.class);
        setUp();

        server.expect(requestTo("http://catalog/api/v1/products/15"))
                .andRespond(withSuccess("""
                        {"id":15,"price":1.239,"currency":"PEN","published":true}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> catalog.getPublishedProduct(15L))
                .isInstanceOf(CatalogIntegrationException.class);
        setUp();

        server.expect(requestTo("http://catalog/api/v1/products/16"))
                .andRespond(withSuccess("""
                        {"id":16,"price":1.00,"currency":"USD","published":true}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> catalog.getPublishedProduct(16L))
                .isInstanceOf(CatalogIntegrationException.class);
        setUp();

        server.expect(requestTo("http://catalog/api/v1/products/17"))
                .andRespond(withSuccess("""
                        {"id":99,"price":1.00,"currency":"PEN","published":true}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> catalog.getPublishedProduct(17L))
                .isInstanceOf(CatalogIntegrationException.class);
        server.verify();
    }
}
