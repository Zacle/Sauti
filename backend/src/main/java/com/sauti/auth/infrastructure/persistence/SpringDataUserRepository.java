package com.sauti.auth.infrastructure.persistence;

import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data implements every method declared on the domain port. */
public interface SpringDataUserRepository extends Repository<User, UUID>, UserRepository {
}
