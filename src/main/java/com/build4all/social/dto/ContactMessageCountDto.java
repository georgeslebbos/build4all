package com.build4all.social.dto;

public class ContactMessageCountDto {
    private Long contactId;
    private Long messageCount;

    public ContactMessageCountDto(Long contactId, Long messageCount) {
        this.contactId = contactId;
        this.messageCount = messageCount;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public Long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Long messageCount) {
        this.messageCount = messageCount;
    }
}
