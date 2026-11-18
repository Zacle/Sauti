package com.sauti.auth.infrastructure.persistence;

import com.sauti.auth.domain.RefreshToken;
import com.sauti.auth.domain.RefreshTokenRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataRefreshTokenRepository extends Repository<RefreshToken, UUID>, RefreshTokenRepository {
}
