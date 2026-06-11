package com.knowflow.service.impl;

import com.knowflow.common.BusinessException;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.service.TaskService;
import com.knowflow.service.security.OwnershipChecker;
import com.knowflow.util.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long KB_ID = 9L;

    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentChunkMapper documentChunkMapper;
    @Mock
    private ParseTaskMapper parseTaskMapper;
    @Mock
    private TaskService taskService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private OwnershipChecker ownershipChecker;

    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(
                documentMapper,
                documentChunkMapper,
                parseTaskMapper,
                taskService,
                fileStorageService,
                ownershipChecker,
                10L
        );
    }

    @Test
    void uploadRejectsFilesLargerThanConfiguredServiceLimit() {
        stubKbOwnership();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                new byte[11]
        );

        assertThatThrownBy(() -> service.upload(USER_ID, KB_ID, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小不能超过");
        verify(fileStorageService, never()).upload(file, "unused");
    }

    @Test
    void uploadRejectsOctetStreamEvenWhenExtensionLooksSupported() {
        stubKbOwnership();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.pdf",
                "application/octet-stream",
                "content".getBytes()
        );

        assertThatThrownBy(() -> service.upload(USER_ID, KB_ID, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件类型与支持格式不匹配");
        verify(fileStorageService, never()).upload(file, "unused");
    }

    @Test
    void uploadAcceptsPdfBasedOnMagicBytesInsteadOfClientContentType() {
        stubKbOwnership();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.pdf",
                "application/octet-stream",
                "%PDF-1.7".getBytes()
        );
        when(fileStorageService.upload(any(), anyString())).thenReturn("stored/note.pdf");
        when(taskService.createParseTask(any(), eq(KB_ID))).thenReturn(99L);

        assertThatCode(() -> service.upload(USER_ID, KB_ID, file)).doesNotThrowAnyException();

        verify(fileStorageService).upload(eq(file), anyString());
    }

    @Test
    void deleteRemovesStoredFileBeforeDatabaseRows() {
        Document doc = document(31L, "users/7/9/manual.pdf");
        when(documentMapper.selectById(31L)).thenReturn(doc);

        service.delete(USER_ID, 31L);

        InOrder order = inOrder(fileStorageService, documentChunkMapper, parseTaskMapper, documentMapper);
        order.verify(documentMapper).selectById(31L);
        order.verify(fileStorageService).delete("users/7/9/manual.pdf");
        order.verify(documentChunkMapper).delete(any());
        order.verify(parseTaskMapper).delete(any());
        order.verify(documentMapper).deleteById(31L);
    }

    @Test
    void deleteStillRemovesDatabaseRowsWhenStoredFileDeletionFails() {
        Document doc = document(32L, "users/7/9/missing.pdf");
        when(documentMapper.selectById(32L)).thenReturn(doc);
        doThrow(new RuntimeException("storage unavailable"))
                .when(fileStorageService).delete("users/7/9/missing.pdf");

        assertThatCode(() -> service.delete(USER_ID, 32L)).doesNotThrowAnyException();

        verify(documentChunkMapper).delete(any());
        verify(parseTaskMapper).delete(any());
        verify(documentMapper).deleteById(32L);
    }

    private void stubKbOwnership() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(KB_ID);
        kb.setUserId(USER_ID);
        when(ownershipChecker.requireKbOwner(USER_ID, KB_ID)).thenReturn(kb);
    }

    private Document document(Long id, String filePath) {
        Document doc = new Document();
        doc.setId(id);
        doc.setKbId(KB_ID);
        doc.setUserId(USER_ID);
        doc.setFilePath(filePath);
        doc.setStatus("DONE");
        return doc;
    }
}
