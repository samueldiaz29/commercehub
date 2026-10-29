package com.commercehub.order.repository;

import com.commercehub.order.domain.Cart;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("""
            select distinct c
            from Cart c
            left join fetch c.items
            where c.customerId = :customerId
            """)
    Optional<Cart> findByCustomerIdWithItems(@Param("customerId") String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customerId = :customerId")
    Optional<Cart> findByCustomerIdForUpdate(@Param("customerId") String customerId);
}
