package com.build4all.admin.repository;

import com.build4all.admin.domain.AppEnvCounter;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AppEnvCounterRepository extends JpaRepository<AppEnvCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AppEnvCounter c where c.envSuffix = :env")
    Optional<AppEnvCounter> findForUpdate(@Param("env") String env);
}