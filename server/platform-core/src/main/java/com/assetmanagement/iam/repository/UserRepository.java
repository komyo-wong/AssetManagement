package com.assetmanagement.iam.repository;

import com.assetmanagement.iam.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"platformRoles", "platformRoles.permissions"})
    @Query("""
            select distinct user
            from IamUser user
            where lower(user.username) = lower(:account)
               or lower(user.email) = lower(:account)
            """)
    Optional<User> findForAuthentication(@Param("account") String account);

    @EntityGraph(attributePaths = {"platformRoles", "platformRoles.permissions"})
    @Query("select distinct user from IamUser user where user.id = :id")
    Optional<User> findWithPlatformAuthoritiesById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"platformRoles"})
    @Query("select distinct user from IamUser user order by user.createdAt")
    java.util.List<User> findAllWithPlatformRoles();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IamUser user
            set user.lastLoginAt = :instant, user.updatedAt = :instant, user.version = user.version + 1
            where user.id = :userId
            """)
    int touchLastLoginAt(@Param("userId") UUID userId, @Param("instant") Instant instant);
}
