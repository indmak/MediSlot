package com.medislot.service.ai;

import java.util.List;

/**
 * 无 DeepSeek 密钥时的降级实现：返回预设话术，保证流程可跑通。
 */
public class MockAiChatClient implements AiChatClient {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AiReply chat(List<ChatMessage> messages) {
        String last = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        String reply;
        if (last.contains("饮食")) {
            reply = "【模拟 AI · 饮食方案草稿】\n1. 清淡为主，少油少盐；\n2. 多饮水，多吃蔬菜水果；\n3. 避免辛辣、生冷、酒精；\n4. 规律三餐，少量多餐。\n（本内容由模拟 AI 生成，仅供演示，需医生核实。）";
        } else {
            reply = "【模拟 AI】我已记录您的描述。请补充：症状持续多久了？有没有发热、疼痛等伴随症状？"
                    + "（当前未配置 DeepSeek 密钥，此为模拟回复。）";
        }
        return new AiReply(reply, "mock", 0, 0);
    }
}
