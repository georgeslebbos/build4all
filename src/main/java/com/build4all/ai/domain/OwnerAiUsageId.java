package com.build4all.ai.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class OwnerAiUsageId implements Serializable {
    private Long ownerId;
    private LocalDate usageDate;

    public OwnerAiUsageId() {}

    public OwnerAiUsageId(Long ownerId, LocalDate usageDate) {
        this.ownerId = ownerId;
        this.usageDate = usageDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OwnerAiUsageId)) return false;
        OwnerAiUsageId that = (OwnerAiUsageId) o;
        return Objects.equals(ownerId, that.ownerId) &&
               Objects.equals(usageDate, that.usageDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, usageDate);
    }
}
