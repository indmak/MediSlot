package com.medislot.service;

import com.medislot.service.ai.AiChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 可见性过滤：患者侧只检索 PUBLIC，管理/维护员问答不过滤。
 */
class RagVisibilityTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final SettingService settingService = mock(SettingService.class);
    private final AiChatClient aiChatClient = mock(AiChatClient.class);

    private RagService ragService() {
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(settingService.getBoolean("rag.enabled")).thenReturn(true);
        when(settingService.getInt("rag.top-k")).thenReturn(3);
        when(settingService.getInt("rag.max-context-chars")).thenReturn(2000);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        return new RagService(provider, aiChatClient, settingService);
    }

    @Test
    void patientGroundingFiltersToPublic() {
        ragService().contextForConsultation("咳嗽");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertTrue(captor.getValue().hasFilterExpression(), "患者侧应带 visibility=PUBLIC 过滤");
    }

    @Test
    void defaultRetrieveIncludesPrivate() {
        ragService().retrieve("咳嗽", 3);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertFalse(captor.getValue().hasFilterExpression(), "默认检索不应过滤");
    }
}
