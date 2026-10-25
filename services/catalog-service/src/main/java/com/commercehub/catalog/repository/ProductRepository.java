package com.commercehub.catalog.repository;

import com.commercehub.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where p.published = true
              and (:categoryId is null or p.category.id = :categoryId)
              and (:search = '' or lower(p.name) like lower(concat('%', :search, '%')))
            """)
    Page<Product> findPublished(@Param("categoryId") Long categoryId,
                                @Param("search") String search,
                                Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.id = :id and p.published = true")
    Optional<Product> findPublishedById(@Param("id") Long id);
}
