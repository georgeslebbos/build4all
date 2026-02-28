package com.build4all.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.build4all.order.domain.OrderSequence;

import jakarta.persistence.LockModeType;

@Repository
public interface OrderSequenceRepository extends JpaRepository<OrderSequence, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from OrderSequence s where s.ownerProjectId = :opId")
  Optional<OrderSequence> findForUpdate(@Param("opId") Long opId);
}
