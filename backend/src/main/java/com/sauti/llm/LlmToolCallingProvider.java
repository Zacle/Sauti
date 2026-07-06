package com.sauti.llm;

public interface LlmToolCallingProvider {
    LlmToolTurnResponse completeTurn(LlmToolTurnContext context);
}
