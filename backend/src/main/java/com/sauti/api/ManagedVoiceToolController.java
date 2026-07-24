package com.sauti.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.call.ManagedVoiceToolService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/managed-voice/{provider}/{callSid}")
public class ManagedVoiceToolController {
    private final ManagedVoiceToolService toolService;

    public ManagedVoiceToolController(ManagedVoiceToolService toolService) {
        this.toolService = toolService;
    }

    @PostMapping(value = "/tool", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> execute(
            @PathVariable String provider,
            @PathVariable String callSid,
            @RequestParam String token,
            @RequestBody JsonNode payload
    ) {
        return toolService.execute(provider, callSid, token, payload);
    }
}
