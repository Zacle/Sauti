package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.tool.AgentTool;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmToolDefinitionTest {
    @Test
    void marksPotentiallyRemoteToolsAsCallerWaitExpected() {
        var tool = mock(AgentTool.class);
        when(tool.getToolName()).thenReturn("lookup_customer_record");
        when(tool.getToolDescription()).thenReturn("Look up a customer");
        when(tool.getParametersSchema()).thenReturn(Map.of("type", "object"));
        when(tool.getFulfillmentType()).thenReturn("webhook");

        assertThat(LlmToolDefinition.from(tool).callerWaitExpected()).isTrue();
    }

    @Test
    void keepsStaticBusinessHoursOnTheFastPath() {
        var tool = mock(AgentTool.class);
        when(tool.getToolName()).thenReturn("get_business_hours");
        when(tool.getToolDescription()).thenReturn("Return configured hours");
        when(tool.getParametersSchema()).thenReturn(Map.of("type", "object"));
        when(tool.getFulfillmentType()).thenReturn("sauti_calendar");

        assertThat(LlmToolDefinition.from(tool).callerWaitExpected()).isFalse();
    }
}
