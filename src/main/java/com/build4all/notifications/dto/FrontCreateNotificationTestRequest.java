package com.build4all.notifications.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


public class FrontCreateNotificationTestRequest {

    private String receiverType;      // USER or OWNER
    private Long receiverId;

    private String notificationType;  // ORDER_CREATED, CHAT_MESSAGE, LOW_STOCK, etc.
    private String title;
    private String body;

    private String payloadJson;

    
    
	public FrontCreateNotificationTestRequest(String receiverType, Long receiverId, String notificationType,
			String title, String body, String payloadJson) {
		super();
		this.receiverType = receiverType;
		this.receiverId = receiverId;
		this.notificationType = notificationType;
		this.title = title;
		this.body = body;
		this.payloadJson = payloadJson;
	}

	public String getReceiverType() {
		return receiverType;
	}

	public void setReceiverType(String receiverType) {
		this.receiverType = receiverType;
	}

	public Long getReceiverId() {
		return receiverId;
	}

	public void setReceiverId(Long receiverId) {
		this.receiverId = receiverId;
	}

	public String getNotificationType() {
		return notificationType;
	}

	public void setNotificationType(String notificationType) {
		this.notificationType = notificationType;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public void setPayloadJson(String payloadJson) {
		this.payloadJson = payloadJson;
	}
    
    
}