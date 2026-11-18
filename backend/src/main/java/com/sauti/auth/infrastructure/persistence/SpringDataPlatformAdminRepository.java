package com.sauti.auth.infrastructure.persistence;

import com.sauti.auth.domain.PlatformAdmin;
import com.sauti.auth.domain.PlatformAdminRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataPlatformAdminRepository extends Repository<PlatformAdmin, UUID>, PlatformAdminRepository {
}
