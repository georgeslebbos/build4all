package com.build4all.ai.service;

import org.springframework.stereotype.Service;

@Service
public class DummyAiProviderService  {

    public String ask(String prompt) {
        // just a quick check: show that we received the item name from context
        String name = extractAfter(prompt, "Name:");
        if (name == null || name.isBlank()) name = "Not available";

        return "Dummy summary ✅\nName: " + name;
    }

    private String extractAfter(String text, String key) {
        int idx = text.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        int end = text.indexOf("\n", start);
        if (end < 0) end = text.length();
        return text.substring(start, end).trim();
    }
}
