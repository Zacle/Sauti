package com.sauti.demo;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoRequestRepository extends JpaRepository<DemoRequest, UUID> {
    Optional<DemoRequest> findFirstByEmailIgnoreCaseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String email, OffsetDateTime since);

    long countByStatus(String status);

    org.springframework.data.domain.Page<DemoRequest> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
