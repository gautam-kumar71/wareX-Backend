package com.inventory.product.repository;

import com.inventory.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByActive(boolean active, Pageable pageable);

    @Query("""
        SELECT p
        FROM Product p
        WHERE (:activeOnly = false OR p.active = true)
          AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(p.category, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Product> searchProducts(@Param("query") String query, @Param("activeOnly") boolean activeOnly, Pageable pageable);
}
