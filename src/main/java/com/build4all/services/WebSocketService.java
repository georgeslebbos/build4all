package com.build4all.services;

import com.build4all.dto.TypingDTO;
import com.build4all.entities.ChatMessages;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    private String roomId(Long a, Long b) {
        return (a < b) ? (a + "_" + b) : (b + "_" + a);
    }

    public void broadcastMessage(ChatMessages message) {
        Long senderId = message.getSender().getId();
        Long receiverId = message.getReceiver().getId();
        messagingTemplate.convertAndSend("/topic/chat/" + roomId(senderId, receiverId), message);
    }

    public void broadcastTyping(TypingDTO typingDTO) {
        String id = roomId(typingDTO.getSenderId(), typingDTO.getReceiverId());
        messagingTemplate.convertAndSend("/topic/typing/" + id, typingDTO);
    }
}
