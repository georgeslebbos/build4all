package com.build4all.order.domain;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_sequences")
public class OrderSequence {

  @Id
  @Column(name = "owner_project_id")
  private Long ownerProjectId;

  @Column(name = "next_seq", nullable = false)
  private Long nextSeq = 1L;

  public Long getOwnerProjectId() { return ownerProjectId; }
  public void setOwnerProjectId(Long ownerProjectId) { this.ownerProjectId = ownerProjectId; }

  public Long getNextSeq() { return nextSeq; }
  public void setNextSeq(Long nextSeq) { this.nextSeq = nextSeq; }
}