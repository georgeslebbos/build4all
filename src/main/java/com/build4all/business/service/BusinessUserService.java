package com.build4all.business.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.build4all.business.dto.BusinessUserDto;
import com.build4all.business.dto.BusinessUserSimpleDto;
import com.build4all.business.domain.BusinessUser;
import com.build4all.business.repository.BusinessUserRepository;
import com.build4all.business.repository.BusinessesRepository;
import com.build4all.user.repository.UserStatusRepository;

import jakarta.transaction.Transactional;

@Service
public class BusinessUserService {

    private final BusinessUserRepository businessUserRepo;
    private final BusinessesRepository businessRepo;
    private final UserStatusRepository statusRepo;

    public BusinessUserService(BusinessUserRepository businessUserRepo, BusinessesRepository businessRepo, UserStatusRepository statusRepo) {
        this.businessUserRepo = businessUserRepo;
        this.businessRepo = businessRepo;
        this.statusRepo = statusRepo;
    }

    public BusinessUser createBusinessUser(Long businessId, BusinessUserDto dto) {
        BusinessUser user = new BusinessUser();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());

        // Optional fields
        user.setUsername(dto.getUsername());
        user.setPasswordHash(dto.getPasswordHash());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setGoogleId(dto.getGoogleId());
        user.setProfilePictureUrl(dto.getProfilePictureUrl());
        user.setIsPublicProfile(dto.getIsPublicProfile());
        user.setCreatedAt(LocalDateTime.now());

        // ✅ Set status
        if (dto.getStatusId() != null) {
            user.setStatus(statusRepo.findById(dto.getStatusId()).orElse(null));
        } else {
            user.setStatus(statusRepo.findByName("CREATED_BY_BUSINESS")
                .orElseThrow(() -> new RuntimeException("Default status 'CREATED_BY_BUSINESS' not found")));
        }
        
        if ((dto.getEmail() == null || dto.getEmail().trim().isEmpty()) &&
        	    (dto.getPhoneNumber() == null || dto.getPhoneNumber().trim().isEmpty())) {
        	    throw new IllegalArgumentException("Either email or phone number must be provided.");
        	}


        // ✅ Set business
        user.setBusiness(businessRepo.findById(businessId)
            .orElseThrow(() -> new RuntimeException("Business not found")));

        return businessUserRepo.save(user);
    }


    @Transactional
    public List<BusinessUserSimpleDto> getUsersByBusiness(Long businessId) {
        List<BusinessUser> users = businessUserRepo.findByBusiness_Id(businessId);
        return users.stream()
                    .map(BusinessUserSimpleDto::new)
                    .collect(Collectors.toList());
    }
    
    
    public BusinessUser findBusinessUserById(Long businessUserId) {
        return businessUserRepo.findById(businessUserId)
            .orElseThrow(() -> new RuntimeException("Business user not found"));
    }

   

    


}

