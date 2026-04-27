package com.nexuslabs.vector.api;

public class AskResponse {
    private String answer;
    private String model;
    private String source;
    private String complexity;
    private Long processingTimeMs;

    public AskResponse() {}

    public AskResponse(String answer, String model, String source, String complexity) {
        this.answer = answer;
        this.model = model;
        this.source = source;
        this.complexity = complexity;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getComplexity() { return complexity; }
    public void setComplexity(String complexity) { this.complexity = complexity; }
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
}