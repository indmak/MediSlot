package com.medislot.dto;

import java.util.List;

/**
 * 知识库问答结果。
 */
public record KnowledgeAnswer(String answer, List<String> citations) {
}
