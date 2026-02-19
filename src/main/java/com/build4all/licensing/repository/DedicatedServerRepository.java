package com.build4all.licensing.repository;

import com.build4all.licensing.domain.DedicatedServer;
import com.build4all.licensing.domain.ServerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DedicatedServerRepository extends JpaRepository<DedicatedServer, Long> {
    List<DedicatedServer> findByStatus(ServerStatus status);
}
