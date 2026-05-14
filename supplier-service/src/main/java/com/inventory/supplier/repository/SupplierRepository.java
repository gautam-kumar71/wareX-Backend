package com.inventory.supplier.repository;

import com.inventory.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Page<Supplier> findByActiveTrue(Pageable pageable);

    Page<Supplier> findByActive(Boolean active, Pageable pageable);

    boolean existsByContactEmail(String email);

    boolean existsByContactEmailAndIdNot(String email, Long id);

    Optional<Supplier> findFirstByIdAndActive(Long id, Boolean active);

    @Query("SELECT s FROM Supplier s WHERE " +
            "CAST(s.id AS string) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(s.contactEmail) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.contactPerson, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.contactPhone, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.city, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.country, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(REPLACE(COALESCE(s.category, ''), '_', ' ')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.category, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.gstin, '')) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Supplier> search(@Param("q") String query, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.active = :active AND (" +
            "CAST(s.id AS string) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(s.contactEmail) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.contactPerson, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.contactPhone, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.city, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.country, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(REPLACE(COALESCE(s.category, ''), '_', ' ')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.category, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(s.gstin, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Supplier> searchByActive(@Param("q") String query, @Param("active") Boolean active, Pageable pageable);

    List<Supplier> findByActiveTrue();
}
