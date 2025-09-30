package com.build4all.controller;

import com.build4all.dto.ChatMessageDto;
import com.build4all.dto.TypingDTO;
import com.build4all.entities.ChatMessages;
import com.build4all.entities.Users;
import com.build4all.services.ChatMessagesService;
import com.build4all.services.UserService;
import com.build4all.services.WebSocketService;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final WebSocketService webSocketService;
    private final ChatMessagesService chatService;
    private final UserService usersService;

    public ChatWebSocketController(WebSocketService webSocketService,
                                   ChatMessagesService chatService,
                                   UserService usersService) {
        this.webSocketService = webSocketService;
        this.chatService = chatService;
        this.usersService = usersService;
    }

    @MessageMapping("/chat")
    public void handleChatMessage(ChatMessageDto dto) {
        Users sender = usersService.getUserById(dto.getSenderId());
        Users receiver = usersService.getUserById(dto.getReceiverId());

        if (sender == null || receiver == null) return;

        ChatMessages chat = chatService.sendMessageWithImage(
            sender, receiver, dto.getMessage(), dto.getImageUrl()
        );

        webSocketService.broadcastMessage(chat);
    }

    @MessageMapping("/typing")
    public void handleTyping(TypingDTO typingDTO) {
        webSocketService.broadcastTyping(typingDTO);
    }
}
