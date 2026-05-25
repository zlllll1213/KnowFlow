package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.ChatAskRequest;
import com.knowflow.dto.ChatSessionCreateRequest;
import com.knowflow.entity.ChatSession;
import com.knowflow.security.LoginUser;
import com.knowflow.service.ChatService;
import com.knowflow.vo.ChatMessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 创建聊天会话 */
    @PostMapping("/session")
    public Result<ChatSession> createSession(@AuthenticationPrincipal LoginUser loginUser,
                                             @Valid @RequestBody ChatSessionCreateRequest request) {
        ChatSession session = chatService.createSession(loginUser.getUserId(), request);
        return Result.success("会话创建成功", session);
    }

    /** 会话列表 */
    @GetMapping("/session/list")
    public Result<List<ChatSession>> listSessions(@AuthenticationPrincipal LoginUser loginUser,
                                                  @RequestParam Long kbId) {
        List<ChatSession> sessions = chatService.listSessions(loginUser.getUserId(), kbId);
        return Result.success(sessions);
    }

    /** 提问 */
    @PostMapping("/ask")
    public Result<ChatMessageVO> ask(@AuthenticationPrincipal LoginUser loginUser,
                                     @Valid @RequestBody ChatAskRequest request) {
        ChatMessageVO answer = chatService.ask(loginUser.getUserId(), request);
        return Result.success(answer);
    }

    /** 流式提问 */
    @PostMapping("/ask/stream")
    public SseEmitter askStream(@AuthenticationPrincipal LoginUser loginUser,
                                @Valid @RequestBody ChatAskRequest request) {
        return chatService.askStream(loginUser.getUserId(), request);
    }

    /** 聊天历史 */
    @GetMapping("/history")
    public Result<List<ChatMessageVO>> history(@AuthenticationPrincipal LoginUser loginUser,
                                               @RequestParam Long sessionId) {
        List<ChatMessageVO> messages = chatService.getHistory(loginUser.getUserId(), sessionId);
        return Result.success(messages);
    }
}
