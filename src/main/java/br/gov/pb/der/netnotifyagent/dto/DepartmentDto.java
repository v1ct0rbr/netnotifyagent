package br.gov.pb.der.netnotifyagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepartmentDto(@JsonProperty("name") String name) {}