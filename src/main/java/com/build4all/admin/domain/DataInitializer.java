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
	            if (roleRepository.findByName("SUPER_ADMIN").isEmpty()) {
	                roleRepository.save(new Role("SUPER_ADMIN"));
	            }
	            if (roleRepository.findByName("MANAGER").isEmpty()) {
	                roleRepository.save(new Role("MANAGER"));
	            }
	        };
	    }
}
