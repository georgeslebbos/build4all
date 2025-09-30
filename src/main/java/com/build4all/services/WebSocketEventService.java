package com.build4all.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

//WebSocketEventService.java
@Service
public class WebSocketEventService {

 private final SimpMessagingTemplate messagingTemplate;

 @Autowired
 public WebSocketEventService(SimpMessagingTemplate messagingTemplate) {
     this.messagingTemplate = messagingTemplate;
 }

 public void sendActivityDeleted(String activityId) {
     messagingTemplate.convertAndSend("/topic/activityDeleted", activityId);
 }
 
 public void sendActivityUpdated(String activityId) {
	    messagingTemplate.convertAndSend("/topic/activityUpdated", activityId);
	}
 
 public void sendPostCreated(String postId) {
	    messagingTemplate.convertAndSend("/topic/postCreated", postId);
	}

	public void sendPostDeleted(String postId) {
	    messagingTemplate.convertAndSend("/topic/postDeleted", postId);
	}


}

