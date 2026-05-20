package com.flight.query.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {

    private String type;
    private String content;
    private String toolName;

    public static StreamEvent token(String content) {
        return new StreamEvent("token", content, null);
    }

    public static StreamEvent toolCall(String toolName) {
        return new StreamEvent("tool_call", null, toolName);
    }

    public static StreamEvent toolResult(String toolName, String result) {
        return new StreamEvent("tool_result", result, toolName);
    }

    public static StreamEvent done() {
        return new StreamEvent("done", null, null);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("error", message, null);
    }
}
