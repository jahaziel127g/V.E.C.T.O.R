package com.nexuslabs.vector.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AskRequest {
    @NotBlank(message = "Question cannot be empty")
    @Size(max = 1000, message = "Question too long")
    private String question;

    private String userId;
    private String ipAddress;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}