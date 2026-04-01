package com.build4all.notifications.dto;

public class CiFirebaseConfigResponse {

    private String fileName;
    private String contentBase64;

    public CiFirebaseConfigResponse() {
    }

    public CiFirebaseConfigResponse(String fileName, String contentBase64) {
        this.fileName = fileName;
        this.contentBase64 = contentBase64;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentBase64() {
        return contentBase64;
    }

    public void setContentBase64(String contentBase64) {
        this.contentBase64 = contentBase64;
    }
}