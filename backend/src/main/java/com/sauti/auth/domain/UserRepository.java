package com.sauti.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port for accounts. Implemented by Spring Data in the infrastructure layer. */
public interface UserRepository {
    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllById(Iterable<UUID> ids);
}
