package model;

import java.time.LocalDateTime;

public class Message {

    private String sender;
    private String receiver;
    private String groupName;
    private String message;
    private String messageType;
    private LocalDateTime timestamp;

    public Message() {
    }

    public Message(
            String sender,
            String receiver,
            String groupName,
            String message,
            String messageType,
            LocalDateTime timestamp) {

        this.sender = sender;
        this.receiver = receiver;
        this.groupName = groupName;
        this.message = message;
        this.messageType = messageType;
        this.timestamp = timestamp;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}