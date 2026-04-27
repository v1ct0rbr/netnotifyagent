package br.gov.pb.der.netnotifyagent.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        @JsonProperty("id")          String id,
        @JsonProperty("title")       String title,
        @JsonProperty("content")     String content,
        @JsonProperty("level")       String level,
        @JsonProperty("type")        String type,
        @JsonProperty("user")        String user,
        @JsonProperty("createdAt")   String createdAt,
        @JsonProperty("updatedAt")   String updatedAt,
        @JsonProperty("departments") List<DepartmentDto> departments) {
}
