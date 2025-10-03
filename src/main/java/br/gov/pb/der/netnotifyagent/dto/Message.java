package br.gov.pb.der.netnotifyagent.dto;

import br.gov.pb.der.netnotifyagent.utils.Functions;

public class Message {

    String id;
    String title;
    String content;
    String level; // info, warning, error, html
    String type; // notification, alert
    String user; // usuário que criou a mensagem
    String createdAt;
    String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Message stringJsonToMessage(String json) {
        return Functions.jsonToObject(json, Message.class);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                "title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", level='" + level + '\'' +
                ", type='" + type + '\'' +
                ", user='" + user + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}
