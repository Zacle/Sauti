package com.sauti.llm;

import java.util.function.Consumer;

public interface LlmToolCallingProvider {
    LlmToolTurnResponse completeTurn(LlmToolTurnContext context);

    default LlmToolTurnResponse streamTurn(LlmToolTurnContext context, Consumer<String> textDeltaConsumer) {
        var response = completeTurn(context);
        if (response.responseText() != null && !response.responseText().isBlank()) {
            textDeltaConsumer.accept(response.responseText());
        }
        return response;
    }
}
