package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.AgentResponse;
import com.knowflow.dto.ChatAskRequest;
import com.knowflow.security.LoginUser;
import com.knowflow.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ChatService chatService;

    @PostMapping("/ask")
    public Result<AgentResponse> ask(@AuthenticationPrincipal LoginUser loginUser,
                                     @Valid @RequestBody ChatAskRequest request) {
        return Result.success(chatService.askAgent(loginUser.getUserId(), request));
    }

    @PostMapping("/ask/stream")
    public SseEmitter askStream(@AuthenticationPrincipal LoginUser loginUser,
                                @Valid @RequestBody ChatAskRequest request) {
        return chatService.askAgentStream(loginUser.getUserId(), request);
    }
}
