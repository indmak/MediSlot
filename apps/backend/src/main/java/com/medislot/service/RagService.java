package com.medislot.service;

import com.medislot.dto.KnowledgeAnswer;
import com.medislot.service.ai.AiChatClient;
import com.medislot.service.ai.AiReply;
import com.medislot.service.ai.ChatMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索服务：知识库问答 + 为诊前咨询提供检索增强上下文。
 */
@Service
public class RagService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final AiChatClient aiChatClient;
    private final SettingService settingService;

    public RagService(ObjectProvider<VectorStore> vectorStoreProvider,
                      AiChatClient aiChatClient,
                      SettingService settingService) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.aiChatClient = aiChatClient;
        this.settingService = settingService;
    }

    public boolean isAvailable() {
        return vectorStoreProvider.getIfAvailable() != null;
    }

    @Transactional(readOnly = true)
    public List<Document> retrieve(String question, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || question == null || question.isBlank()) {
            return List.of();
        }
        int k = topK > 0 ? topK : 4;
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(k)
                .similarityThreshold(0.0)
                .build());
    }

    /** 为诊前咨询构建知识库参考上下文；关闭或无命中时返回空串。 */
    public String contextForConsultation(String query) {
        if (!isAvailable() || !settingService.getBoolean("rag.enabled")) {
            return "";
        }
        int topK = settingService.getInt("rag.top-k");
        if (topK <= 0) {
            topK = 3;
        }
        List<Document> docs = retrieve(query, topK);
        if (docs.isEmpty()) {
            return "";
        }
        int maxChars = settingService.getInt("rag.max-context-chars");
        if (maxChars <= 0) {
            maxChars = 2000;
        }
        StringBuilder sb = new StringBuilder("\n\n【知识库参考片段（可能相关，仅供参考，未必适用于当前患者）】\n");
        for (Document doc : docs) {
            sb.append("- ").append(doc.getText()).append('\n');
            if (sb.length() >= maxChars) {
                break;
            }
        }
        return sb.toString();
    }

    /** 知识库问答：检索 + 大模型生成（带来源）。 */
    public KnowledgeAnswer answer(String question, int topK) {
        List<Document> docs = retrieve(question, topK);
        List<String> citations = docs.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("title", "")))
                .filter(t -> !t.isBlank())
                .distinct()
                .toList();

        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(
                "你是 MediSlot 知识库助手。请**仅依据**下面提供的知识库片段回答问题；"
                        + "若片段不足以回答，请明确说明「知识库中没有相关信息」。回答简洁、分点，不要编造。"));
        StringBuilder user = new StringBuilder("【知识库片段】\n");
        for (int i = 0; i < docs.size(); i++) {
            user.append("[").append(i + 1).append("] ").append(docs.get(i).getText()).append('\n');
        }
        user.append("\n【问题】").append(question);
        prompt.add(ChatMessage.user(user.toString()));

        AiReply reply = aiChatClient.chat(prompt);
        return new KnowledgeAnswer(reply.content(), citations);
    }
}
