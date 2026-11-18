package com.sauti.tenant.infrastructure;

import com.sauti.tenant.domain.ApiKey;
import com.sauti.tenant.domain.ApiKeyRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataApiKeyRepository extends Repository<ApiKey, UUID>, ApiKeyRepository {
}
