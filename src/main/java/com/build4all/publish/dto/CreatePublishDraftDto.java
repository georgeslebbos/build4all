package com.build4all.publish.dto;

import com.build4all.publish.domain.PublishPlatform;
import com.build4all.publish.domain.PublishStore;
import jakarta.validation.constraints.NotNull;

public class CreatePublishDraftDto {

    @NotNull
    private Long aupId;

    @NotNull
    private PublishPlatform platform; // ANDROID / IOS

    @NotNull
    private PublishStore store; // PLAY_STORE / APP_STORE 

    public Long getAupId() { return aupId; }
    public void setAupId(Long aupId) { this.aupId = aupId; }

    public PublishPlatform getPlatform() { return platform; }
    public void setPlatform(PublishPlatform platform) { this.platform = platform; }

    public PublishStore getStore() { return store; }
    public void setStore(PublishStore store) { this.store = store; }
}
