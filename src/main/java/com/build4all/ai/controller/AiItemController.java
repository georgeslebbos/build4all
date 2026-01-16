package com.build4all.ai.controller;

import com.build4all.ai.dto.AiChatResponse;
import com.build4all.ai.dto.AiItemChatRequest;
import com.build4all.ai.service.AiItemChatService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiItemController {

    private final AiItemChatService service;

    public AiItemController(AiItemChatService service) {
        this.service = service;
    }

    @PostMapping("/item-chat")
    public AiChatResponse itemChat(@RequestBody AiItemChatRequest req) {
        return new AiChatResponse(service.handle(req));
    }
}

