package com.build4all.licensing.repository;

import com.build4all.licensing.domain.PlanCatalog;
import com.build4all.licensing.domain.PlanCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCatalogRepository extends JpaRepository<PlanCatalog, PlanCode> {}
