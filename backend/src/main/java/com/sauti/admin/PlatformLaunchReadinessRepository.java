package com.sauti.admin;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface PlatformLaunchReadinessRepository extends JpaRepository<PlatformLaunchReadiness, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select readiness from PlatformLaunchReadiness readiness where readiness.id = :id")
    Optional<PlatformLaunchReadiness> findByIdForUpdate(String id);
}
