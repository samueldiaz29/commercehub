package com.commercehub.order.application;

import com.commercehub.order.client.CatalogClient;
import com.commercehub.order.client.CatalogProduct;
import com.commercehub.order.domain.CatalogIntegrationException;
import com.commercehub.order.domain.Cart;
import com.commercehub.order.domain.InvalidCartQuantityException;
import com.commercehub.order.domain.ProductUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    CartPersistenceService persistence;

    @Mock
    CatalogClient catalog;

    @InjectMocks
    CartService service;

    @Test
    void createsCartAndCalculatesPENAmounts() {
        when(catalog.getPublishedProduct(9L))
                .thenReturn(new CatalogProduct(9L, new BigDecimal("12.50"), "PEN", true));
        when(persistence.add("user-a", 9L, 2, List.of()))
                .thenReturn(List.of(new CartLine(9L, 2)));

        var cart = service.add("user-a", 9L, 2);

        assertThat(cart.subtotal()).isEqualByComparingTo("25.00");
        assertThat(cart.shipping()).isEqualByComparingTo("10.00");
        assertThat(cart.total()).isEqualByComparingTo("35.00");
        verify(persistence).add("user-a", 9L, 2, List.of());
    }

    @Test
    void calculatesSubtotalShippingAndTotalForMultipleProducts() {
        when(persistence.getItems("user-a"))
                .thenReturn(List.of(new CartLine(9L, 2), new CartLine(10L, 1)));
        when(catalog.getPublishedProduct(9L))
                .thenReturn(new CatalogProduct(9L, new BigDecimal("4.25"), "PEN", true));
        when(catalog.getPublishedProduct(10L))
                .thenReturn(new CatalogProduct(10L, new BigDecimal("11.50"), "PEN", true));

        var cart = service.get("user-a");

        assertThat(cart.subtotal()).isEqualByComparingTo("20.00");
        assertThat(cart.shipping()).isEqualByComparingTo("10.00");
        assertThat(cart.total()).isEqualByComparingTo("30.00");
    }

    @Test
    void updatesAndRemovesItemsThroughThePersistenceBoundary() {
        when(persistence.getItems("user-a")).thenReturn(List.of(new CartLine(9L, 1)));
        when(persistence.update("user-a", 9L, 3, List.of(new CartLine(9L, 1))))
                .thenReturn(List.of(new CartLine(9L, 3)));
        when(catalog.getPublishedProduct(9L))
                .thenReturn(new CatalogProduct(9L, new BigDecimal("4.00"), "PEN", true));

        var updated = service.update("user-a", 9L, 3);
        var removed = service.remove("user-a", 9L);

        assertThat(updated.subtotal()).isEqualByComparingTo("12.00");
        assertThat(removed.items()).isEmpty();
        assertThat(removed.total()).isEqualByComparingTo("0.00");
        verify(persistence).update("user-a", 9L, 3, List.of(new CartLine(9L, 1)));
        verify(persistence).remove("user-a", 9L, List.of(new CartLine(9L, 1)));
    }

    @Test
    void rejectsInvalidQuantityBeforePersistence() {
        assertThatThrownBy(() -> service.add("u", 1L, 0))
                .isInstanceOf(InvalidCartQuantityException.class);
        assertThatThrownBy(() -> service.update("u", 1L, 0))
                .isInstanceOf(InvalidCartQuantityException.class);

        verify(persistence, never()).add(anyString(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.anyList());
        verify(persistence, never()).update(anyString(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void mapsQuantityAdditionOverflowToDomainValidationError() {
        Cart cart = new Cart("user-a");
        cart.addProduct(9L, Integer.MAX_VALUE);

        assertThatThrownBy(() -> cart.addProduct(9L, 1))
                .isInstanceOf(InvalidCartQuantityException.class)
                .hasMessage("La cantidad acumulada supera el máximo permitido.");
    }

    @Test
    void rejectsMissingProductAndCatalogFailureBeforeAdding() {
        when(catalog.getPublishedProduct(8L)).thenThrow(new ProductUnavailableException(8L));
        assertThatThrownBy(() -> service.add("u", 8L, 1))
                .isInstanceOf(ProductUnavailableException.class);
        verify(persistence, never()).add(anyString(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.anyList());

        when(catalog.getPublishedProduct(5L))
                .thenThrow(new CatalogIntegrationException("Catálogo no disponible.", null));
        assertThatThrownBy(() -> service.add("u", 5L, 1))
                .isInstanceOf(CatalogIntegrationException.class);
        verify(persistence, never()).add(anyString(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void isolatesDifferentCustomersAndHandlesEmptyCart() {
        when(persistence.getItems("alice")).thenReturn(List.of());

        assertThat(service.get("alice").items()).isEmpty();
        assertThat(service.get("alice").total()).isEqualByComparingTo("0.00");

        verify(persistence, times(2)).getItems("alice");
        verify(persistence, never()).getItems("bob");
    }
}
