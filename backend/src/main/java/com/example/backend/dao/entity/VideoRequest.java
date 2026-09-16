package com.example.backend.dao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VideoRequest {

    private String text;

    @JsonProperty("image_filename")
    private String imageFilename;

    @JsonProperty("voice_id")
    private String voiceId;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public void setImageFilename(String imageFilename) {
        this.imageFilename = imageFilename;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }
}
