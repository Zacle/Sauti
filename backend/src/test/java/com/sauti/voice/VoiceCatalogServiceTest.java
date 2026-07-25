package com.sauti.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VoiceCatalogServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsAnEmptyCatalogWhenTelnyxIsNotConfigured() {
        var client = mock(TelnyxVoiceCatalogClient.class);
        when(client.isConfigured()).thenReturn(false);

        var catalog = new VoiceCatalogService(client).list();

        assertThat(catalog.enabledProviders()).isEmpty();
        assertThat(catalog.voices()).isEmpty();
    }

    @Test
    void mapsOnlyNativeTelnyxVoicesAndMergesTheirLanguages() throws Exception {
        var client = mock(TelnyxVoiceCatalogClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.listNativeVoices()).thenReturn(objectMapper.readTree("""
                {
                  "voices": [
                    {
                      "provider": "telnyx",
                      "name": "Astra",
                      "voice_id": "Telnyx.NaturalHD.astra",
                      "language": "en-US",
                      "gender": "female"
                    },
                    {
                      "provider": "telnyx",
                      "name": "Astra",
                      "voice_id": "Telnyx.NaturalHD.astra",
                      "language": "fr-FR",
                      "gender": "female"
                    },
                    {
                      "provider": "aws",
                      "name": "Joanna",
                      "voice_id": "AWS.Polly.Joanna",
                      "language": "en-US",
                      "gender": "female"
                    }
                  ]
                }
                """));

        var catalog = new VoiceCatalogService(client).list();

        assertThat(catalog.enabledProviders()).containsExactly("telnyx");
        assertThat(catalog.voices()).singleElement().satisfies(voice -> {
            assertThat(voice.id()).isEqualTo("Telnyx.NaturalHD.astra");
            assertThat(voice.languages()).containsExactly("en", "fr");
            assertThat(voice.traits()).containsEntry("model", "NaturalHD");
            assertThat(voice.traits()).containsEntry("gender", "female");
        });
        verify(client).listNativeVoices();
    }

    @Test
    void normalizesTelnyxDisplayLanguageNamesToStableCodes() throws Exception {
        var client = mock(TelnyxVoiceCatalogClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.listNativeVoices()).thenReturn(objectMapper.readTree("""
                {
                  "voices": [
                    {
                      "provider": "telnyx",
                      "name": "Asher",
                      "voice_id": "Telnyx.Ultra.asher",
                      "language": "English",
                      "gender": "male"
                    },
                    {
                      "provider": "telnyx",
                      "name": "Clara",
                      "voice_id": "Telnyx.Ultra.clara",
                      "language": "American English",
                      "gender": "female"
                    },
                    {
                      "provider": "telnyx",
                      "name": "Huda",
                      "voice_id": "Telnyx.Ultra.huda",
                      "language": "Arabic",
                      "gender": "female"
                    }
                  ]
                }
                """));

        var catalog = new VoiceCatalogService(client).list();

        assertThat(catalog.voices())
                .filteredOn(voice -> voice.languages().contains("en"))
                .extracting(voice -> voice.id())
                .containsExactlyInAnyOrder("Telnyx.Ultra.asher", "Telnyx.Ultra.clara");
        assertThat(catalog.voices())
                .filteredOn(voice -> voice.languages().contains("ar"))
                .extracting(voice -> voice.id())
                .containsExactly("Telnyx.Ultra.huda");
    }

    @Test
    void mapsTheCurrentTelnyxCatalogIdAndModelFields() throws Exception {
        var client = mock(TelnyxVoiceCatalogClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.listNativeVoices()).thenReturn(objectMapper.readTree("""
                {
                  "voices": [
                    {
                      "id": "Telnyx.Bayan.Amanda",
                      "label": "en",
                      "name": "Amanda",
                      "language": "en-US",
                      "model_id": "Bayan",
                      "accent": "American",
                      "gender": "female",
                      "provider": "telnyx",
                      "is_platform": true
                    },
                    {
                      "id": "Telnyx.Bayan.Alia",
                      "label": "uae",
                      "name": "Alia",
                      "language": "ar-AE",
                      "model_id": "Bayan",
                      "gender": "female",
                      "provider": "telnyx",
                      "is_platform": true
                    }
                  ]
                }
                """));

        var catalog = new VoiceCatalogService(client).list();

        assertThat(catalog.voices()).hasSize(2);
        assertThat(catalog.voices())
                .filteredOn(voice -> voice.id().equals("Telnyx.Bayan.Amanda"))
                .singleElement()
                .satisfies(voice -> {
                    assertThat(voice.name()).isEqualTo("Amanda");
                    assertThat(voice.languages()).containsExactly("en");
                    assertThat(voice.traits()).containsEntry("model", "Bayan");
                    assertThat(voice.traits()).containsEntry("accent", "American");
                });
        assertThat(catalog.voices())
                .filteredOn(voice -> voice.id().equals("Telnyx.Bayan.Alia"))
                .singleElement()
                .satisfies(voice -> assertThat(voice.languages()).containsExactly("ar"));
    }

    @Test
    void cachesAnExactTelnyxPreview() throws Exception {
        var client = mock(TelnyxVoiceCatalogClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.listNativeVoices()).thenReturn(objectMapper.readTree("""
                {"voices":[{
                  "provider":"telnyx",
                  "name":"Astra",
                  "voice_id":"Telnyx.NaturalHD.astra",
                  "language":"fr-FR",
                  "gender":"female"
                }]}
                """));
        when(client.synthesize(
                "Telnyx.NaturalHD.astra",
                "fr",
                "Bonjour, comment puis-je vous aider ?"
        )).thenReturn(new byte[] {1, 2, 3});
        var service = new VoiceCatalogService(client);

        assertThat(service.preview(
                "Telnyx.NaturalHD.astra", "fr", "Bonjour, comment puis-je vous aider ?"
        )).containsExactly(1, 2, 3);
        assertThat(service.preview(
                "Telnyx.NaturalHD.astra", "fr", "Bonjour, comment puis-je vous aider ?"
        )).containsExactly(1, 2, 3);

        verify(client, times(1)).synthesize(
                "Telnyx.NaturalHD.astra",
                "fr",
                "Bonjour, comment puis-je vous aider ?"
        );
    }
}
