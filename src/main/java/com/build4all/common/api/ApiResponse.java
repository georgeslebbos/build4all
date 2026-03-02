package com.build4all.common.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponse {

    private ApiResponse() {}

    public static Map<String, Object> ok(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        return m;
    }

    public static Map<String, Object> ok(String message, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> err(String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        return m;
    }

    public static Map<String, Object> err(String error, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("code", code);
        return m;
    }
}