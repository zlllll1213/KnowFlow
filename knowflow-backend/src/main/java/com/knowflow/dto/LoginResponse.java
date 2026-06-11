package com.knowflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private Long userId;
    private String username;
    @JsonIgnore
    private String token;
}
