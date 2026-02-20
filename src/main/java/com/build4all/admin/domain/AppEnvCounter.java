package com.build4all.admin.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_env_counter")
public class AppEnvCounter {

    @Id
    @Column(name = "env_suffix", length = 16)
    private String envSuffix;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber;

    public String getEnvSuffix() { return envSuffix; }
    public void setEnvSuffix(String envSuffix) { this.envSuffix = envSuffix; }

    public Integer getNextNumber() { return nextNumber; }
    public void setNextNumber(Integer nextNumber) { this.nextNumber = nextNumber; }
}