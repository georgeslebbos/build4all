package com.build4all.catalog.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.ItemAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemAttributeRepository extends JpaRepository<ItemAttribute, Long> {

    List<ItemAttribute> findByOwnerProject(AdminUserProject ownerProject);

    Optional<ItemAttribute> findByOwnerProjectAndCode(AdminUserProject ownerProject, String code);
}
