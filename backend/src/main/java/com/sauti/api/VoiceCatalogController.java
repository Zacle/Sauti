package com.sauti.api;

import com.sauti.voice.VoiceCatalogDtos.VoiceCatalogResponse;
import com.sauti.voice.VoiceCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/voices")
public class VoiceCatalogController {
    private final VoiceCatalogService voiceCatalogService;

    public VoiceCatalogController(VoiceCatalogService voiceCatalogService) {
        this.voiceCatalogService = voiceCatalogService;
    }

    @GetMapping
    VoiceCatalogResponse list() {
        return voiceCatalogService.list();
    }

    @GetMapping(value = "/{voiceId}/preview", produces = "audio/mpeg")
    ResponseEntity<byte[]> preview(
            @PathVariable String voiceId,
            @RequestParam(defaultValue = "en") String language
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .body(voiceCatalogService.preview(voiceId, language));
    }
}
