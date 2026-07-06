package com.sauti.agent;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "fake", matchIfMissing = true)
public class FakeTelephonyProvider implements TelephonyProvider {
    private static final AtomicInteger sequence = new AtomicInteger(1000);

    @Override
    public String provisionNumber(String tenantCountryCode) {
        return "+1555" + sequence.getAndIncrement();
    }

    @Override
    public String buildMediaStreamTwiMl(
            String websocketUrl,
            String callSid,
            String tenantId,
            String agentId,
            boolean recordCall,
            java.util.Map<String, String> extraParameters
    ) {
        var extra = extraParameters.entrySet().stream()
                .map(entry -> "      <Parameter name=\"" + escapeXml(entry.getKey())
                        + "\" value=\"" + escapeXml(entry.getValue()) + "\"/>")
                .collect(java.util.stream.Collectors.joining("\n"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                  <Connect>
                    <Stream url="%s">
                      <Parameter name="callSid" value="%s"/>
                      <Parameter name="tenantId" value="%s"/>
                      <Parameter name="agentId" value="%s"/>
                %s
                    </Stream>
                  </Connect>
                </Response>
                """.formatted(escapeXml(websocketUrl), escapeXml(callSid), escapeXml(tenantId), escapeXml(agentId), extra);
    }

    private String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
