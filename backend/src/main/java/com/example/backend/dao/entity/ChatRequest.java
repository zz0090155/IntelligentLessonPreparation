package com.example.backend.dao.entity;

import java.util.List;

public class ChatRequest {

    private List<ChatMessage> messages;
    private Boolean useKnowledgeBase;
    private String imageFilename;
    private String conversationKey;

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public Boolean getUseKnowledgeBase() {
        return useKnowledgeBase;
    }

    public void setUseKnowledgeBase(Boolean useKnowledgeBase) {
        this.useKnowledgeBase = useKnowledgeBase;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public void setImageFilename(String imageFilename) {
        this.imageFilename = imageFilename;
    }

    public String getConversationKey() {
        return conversationKey;
    }

    public void setConversationKey(String conversationKey) {
        this.conversationKey = conversationKey;
    }

    public static class ChatMessage {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
