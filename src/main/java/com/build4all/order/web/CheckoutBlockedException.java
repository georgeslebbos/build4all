package com.build4all.order.web;

import java.util.List;
import java.util.Map;

public class CheckoutBlockedException extends RuntimeException {
    private final List<String> blockingErrors;
    private final List<Map<String, Object>> lineErrors;

    public CheckoutBlockedException(List<String> blockingErrors, List<Map<String, Object>> lineErrors) {
        super("Checkout blocked");
        this.blockingErrors = blockingErrors;
        this.lineErrors = lineErrors;
    }

    public List<String> getBlockingErrors() { return blockingErrors; }
    public List<Map<String, Object>> getLineErrors() { return lineErrors; }
}
