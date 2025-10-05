package com.build4all.repositories;

import com.build4all.entities.AdminUsers;
import com.build4all.entities.Businesses;
import com.build4all.entities.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AdminUsersRepository extends JpaRepository<AdminUsers, Long> {

    
    Optional<AdminUsers> findByUsername(String username);
    
    Optional<AdminUsers> findByEmail(String email);
    
    Optional<AdminUsers> findByUsernameOrEmail(String username, String email);
    
    List<AdminUsers> findByEmailAndBusiness(String email, Businesses business);

	List<AdminUsers> findAllByEmail(String email);

	boolean existsByEmail(String superAdminEmail);

	Optional<AdminUsers> findByAdminId(Long adminId);
	
	void deleteByBusinessId(Long adminId);

	int countByRoleNameIgnoreCase(String string);




}
