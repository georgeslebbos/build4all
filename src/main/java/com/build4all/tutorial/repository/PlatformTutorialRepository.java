package com.build4all.tutorial.repository;

import com.build4all.tutorial.domain.PlatformTutorial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformTutorialRepository extends JpaRepository<PlatformTutorial, String> {}