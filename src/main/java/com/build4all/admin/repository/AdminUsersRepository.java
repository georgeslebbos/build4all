package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUsers;
import com.build4all.business.domain.Businesses;

import org.springframework.data.jpa.repository.JpaRepository;

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
