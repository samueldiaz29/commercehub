package com.commercehub.catalog.repository;

import com.commercehub.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
