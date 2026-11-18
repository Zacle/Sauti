package com.sauti.auth.infrastructure.persistence;

import com.sauti.auth.domain.UserIdentity;
import com.sauti.auth.domain.UserIdentityRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataUserIdentityRepository extends Repository<UserIdentity, UUID>, UserIdentityRepository {
}
