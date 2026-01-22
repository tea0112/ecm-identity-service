package com.ecm.security.identity.user.repository;

import com.ecm.security.identity.user.entity.UserEntity;
import io.lettuce.core.dynamic.annotation.Param;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @Query(
        "SELECT u FROM UserEntity u LEFT JOIN FETCH u.roleAssignments WHERE u.username = :username"
    )
    Optional<UserEntity> findByUsernameWithRoles(
        @Param("username") String username
    );

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
