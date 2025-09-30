package com.build4all.repositories;

import com.build4all.entities.Interests;
import com.build4all.entities.UserStatus;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterestsRepository extends JpaRepository<Interests, Long> {

	Optional<Interests> findByName(String name);

	boolean existsByNameIgnoreCase(String name);

	Optional<Interests> findByNameIgnoreCase(String name);

}
