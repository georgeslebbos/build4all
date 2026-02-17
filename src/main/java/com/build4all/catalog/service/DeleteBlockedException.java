// com/build4all/catalog/service/DeleteBlockedException.java
package com.build4all.catalog.service;

public class DeleteBlockedException extends RuntimeException {
    private final String code;
    private final long count;

    public DeleteBlockedException(String code, long count) {
        super(code);
        this.code = code;
        this.count = count;
    }

    public String getCode() { return code; }
    public long getCount() { return count; }
}
