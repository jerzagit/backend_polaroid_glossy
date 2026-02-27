package com.polaroid.repository;

import com.polaroid.model.User;
import com.polaroid.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAffiliateCode(String affiliateCode);
    boolean existsByEmail(String email);
    boolean existsByAffiliateCode(String affiliateCode);
    Page<User> findByRole(Role role, Pageable pageable);
    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Pageable pageable);
    long countByRole(Role role);
}
