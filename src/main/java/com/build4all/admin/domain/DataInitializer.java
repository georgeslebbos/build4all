package com.build4all.admin.domain;
import com.build4all.role.domain.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.build4all.role.repository.RoleRepository;

@Configuration
public class DataInitializer {

	@Bean
	public CommandLineRunner initRoles(RoleRepository roleRepository) {
	    return args -> {
	        if (!roleRepository.existsByNameIgnoreCase("SUPER_ADMIN")) {
	            roleRepository.save(new Role("SUPER_ADMIN"));
	        }
	        if (!roleRepository.existsByNameIgnoreCase("MANAGER")) {
	            roleRepository.save(new Role("MANAGER"));
	        }
	        if (!roleRepository.existsByNameIgnoreCase("OWNER")) {
	            roleRepository.save(new Role("OWNER"));
	        }
	    };
	}

}
