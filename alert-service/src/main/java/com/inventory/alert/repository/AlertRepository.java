package com.inventory.alert.repository;

import com.inventory.alert.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    boolean existsByEventId(String eventId);
    List<Alert> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Alert> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);
    List<Alert> findByUserIdIsNullOrderByCreatedAtDesc();
    List<Alert> findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(String userId);
    Optional<Alert> findByIdAndUserId(Long id, String userId);
    @Transactional
    long deleteByUserIdOrUserIdIsNull(String userId);

    @Transactional
    long deleteByUserIdIsNull();
}
