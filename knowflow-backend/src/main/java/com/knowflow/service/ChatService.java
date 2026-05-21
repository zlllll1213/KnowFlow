package com.knowflow.service;

import com.knowflow.dto.ChatAskRequest;
import com.knowflow.dto.ChatSessionCreateRequest;
import com.knowflow.entity.ChatSession;
import com.knowflow.vo.ChatMessageVO;

import java.util.List;

public interface ChatService {

    /** 创建聊天会话 */
    ChatSession createSession(Long userId, ChatSessionCreateRequest request);

    /** 获取知识库下的会话列表 */
    List<ChatSession> listSessions(Long userId, Long kbId);

    /** 提问，返回模拟回答 */
    ChatMessageVO ask(Long userId, ChatAskRequest request);

    /** 获取会话历史消息 */
    List<ChatMessageVO> getHistory(Long userId, Long sessionId);
}
