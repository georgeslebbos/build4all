package com.build4all.role.repository;

import com.build4all.role.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findTopByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

   
    @Deprecated
    default Optional<Role> findByName(String name) {
        return findTopByNameIgnoreCase(name);
    }
}
