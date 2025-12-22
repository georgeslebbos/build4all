package com.build4all.security;

public final class TenantContext {

    private static final ThreadLocal<Long> OWNER_PROJECT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOwnerProjectId(Long ownerProjectId) {
        OWNER_PROJECT_ID.set(ownerProjectId);
    }

    public static Long getOwnerProjectId() {
        return OWNER_PROJECT_ID.get();
    }

    public static void clear() {
        OWNER_PROJECT_ID.remove();
    }
}
