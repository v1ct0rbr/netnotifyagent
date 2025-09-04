package br.gov.pb.der.netnotifyagent.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message {

    String content;
    String level; // info, warning, error, html
    String type; // notification, alert
    String user; // usuário que criou a mensagem
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
