package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUsersRepository extends JpaRepository<AdminUser, Long> {

    
    Optional<AdminUser> findByUsername(String username);
    
    Optional<AdminUser> findByEmail(String email);
    
    Optional<AdminUser> findByUsernameOrEmail(String username, String email);
    
    List<AdminUser> findByEmailAndBusiness(String email, Businesses business);

	List<AdminUser> findAllByEmail(String email);

	boolean existsByEmail(String superAdminEmail);

	Optional<AdminUser> findByAdminId(Long adminId);

	void deleteByBusiness_Id(Long businessId);

	int countByRoleNameIgnoreCase(String string);




}
