package com.commercehub.catalog.repository;

import com.commercehub.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select distinct c from Category c join Product p on p.category = c where p.published = true order by c.name, c.id")
    List<Category> findAllWithPublishedProducts();
}
