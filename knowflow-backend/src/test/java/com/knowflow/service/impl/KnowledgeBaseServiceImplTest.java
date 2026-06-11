package com.knowflow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.BusinessException;
import com.knowflow.common.PageResult;
import com.knowflow.dto.KbCreateRequest;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.service.DocumentService;
import com.knowflow.service.security.OwnershipChecker;
import com.knowflow.vo.KbVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private KnowledgeBaseMapper kbMapper;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentService documentService;
    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private OwnershipChecker ownershipChecker;

    private KnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseServiceImpl(
                kbMapper,
                documentMapper,
                documentService,
                chatSessionMapper,
                chatMessageMapper,
                ownershipChecker
        );
    }

    @Test
    void createRejectsNameThatIsBlankAfterTrim() {
        KbCreateRequest request = new KbCreateRequest();
        request.setName("   ");
        request.setDescription("空白名称");

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库名称不能为空");

        verify(kbMapper, never()).insert(any());
    }

    @Test
    void listUsesSingleBatchQueryForDocumentCounts() {
        KnowledgeBase first = kb(1L, "产品资料");
        KnowledgeBase second = kb(2L, "研发笔记");
        Page<KnowledgeBase> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(first, second));
        when(kbMapper.selectPage(any(), any())).thenReturn(page);
        when(documentMapper.selectKbDocumentCounts(eq(USER_ID), eq(List.of(1L, 2L))))
                .thenReturn(List.of(
                        Map.of("kbId", 1L, "documentCount", 3L, "doneCount", 2L),
                        Map.of("kbId", 2L, "documentCount", 1L, "doneCount", 1L)
                ));

        PageResult<KbVO> result = service.list(USER_ID, 1, 10);

        assertThat(result.getRecords())
                .extracting(KbVO::getDocumentCount)
                .containsExactly(3L, 1L);
        assertThat(result.getRecords())
                .extracting(KbVO::getDoneCount)
                .containsExactly(2L, 1L);
        verify(documentMapper).selectKbDocumentCounts(eq(USER_ID), eq(List.of(1L, 2L)));
        verify(documentMapper, never()).selectCount(any());
    }

    @Test
    void deleteRemovesDocumentsBeforeChatAndKnowledgeBaseRows() {
        KnowledgeBase kb = kb(11L, "产品资料");
        Document firstDoc = doc(101L);
        Document secondDoc = doc(102L);
        when(ownershipChecker.requireKbOwner(USER_ID, 11L)).thenReturn(kb);
        when(documentMapper.selectList(any())).thenReturn(List.of(firstDoc, secondDoc));

        service.delete(USER_ID, 11L);

        InOrder order = inOrder(documentMapper, documentService, chatMessageMapper, chatSessionMapper, kbMapper);
        order.verify(documentMapper).selectList(any());
        order.verify(documentService).delete(USER_ID, 101L);
        order.verify(documentService).delete(USER_ID, 102L);
        order.verify(chatMessageMapper).delete(any());
        order.verify(chatSessionMapper).delete(any());
        order.verify(kbMapper).deleteById(11L);
    }

    private KnowledgeBase kb(Long id, String name) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setUserId(USER_ID);
        kb.setName(name);
        kb.setDescription("");
        kb.setCreatedAt(LocalDateTime.now());
        kb.setUpdatedAt(LocalDateTime.now());
        return kb;
    }

    private Document doc(Long id) {
        Document doc = new Document();
        doc.setId(id);
        doc.setKbId(11L);
        doc.setUserId(USER_ID);
        doc.setStatus("DONE");
        return doc;
    }
}
