package com.sauti.call;

import com.sauti.voice.TelnyxVoiceCompatibility;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class TelnyxManagedVoiceAgentProvisioner {
    private final ManagedVoiceProviderHttpClient httpClient;
    private final String apiKey;
    private final String apiBaseUrl;
    private final String publicBaseUrl;
    private final String toolWebhookSecret;
    private final String defaultVoiceId;
    private final String aiModel;

    public TelnyxManagedVoiceAgentProvisioner(
            ManagedVoiceProviderHttpClient httpClient,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl,
            @Value("${sauti.telnyx.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${sauti.telnyx.tool-webhook-secret:}") String toolWebhookSecret,
            @Value("${sauti.telnyx.default-voice-id:Telnyx.NaturalHD.astra}") String defaultVoiceId,
            @Value("${sauti.telnyx.ai-model:moonshotai/Kimi-K2.6}") String aiModel
    ) {
        this.httpClient = httpClient;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.toolWebhookSecret = trim(toolWebhookSecret);
        this.defaultVoiceId = trim(defaultVoiceId);
        this.aiModel = trim(aiModel).isBlank() ? "moonshotai/Kimi-K2.6" : trim(aiModel);
    }

    public String provider() {
        return "telnyx";
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !publicBaseUrl.isBlank() && !toolWebhookSecret.isBlank();
    }

    public String configurationVersion() {
        return "46";
    }

    public ManagedVoiceAgentReference synchronize(
            ManagedVoiceAgentBlueprint blueprint,
        ManagedVoiceAgentReference existing
    ) {
        var headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        var updating = existing != null && !existing.externalAgentId().isBlank();
        var body = assistantBody(blueprint, updating);
        com.fasterxml.jackson.databind.JsonNode response;
        if (!updating) {
            response = httpClient.post(
                    "Telnyx",
                    URI.create(apiBaseUrl + "/ai/assistants"),
                    headers,
                    body
            );
        } else {
            response = httpClient.post(
                    "Telnyx",
                    URI.create(apiBaseUrl + "/ai/assistants/" + path(existing.externalAgentId())),
                    headers,
                    body
            );
        }
        var agentId = response.path("id").asText(existing == null ? "" : existing.externalAgentId()).trim();
        if (agentId.isBlank()) throw new IllegalStateException("Telnyx did not return an assistant id");
        var versionId = response.path("version_id").asText(
                existing == null ? "main" : existing.externalVersionId()
        );
        return new ManagedVoiceAgentReference(agentId, versionId, "{}");
    }

    private Map<String, Object> assistantBody(
            ManagedVoiceAgentBlueprint blueprint,
            boolean updating
    ) {
        var tools = new ArrayList<Map<String, Object>>();
        blueprint.tools().forEach(tool -> {
            // Telnyx must have exactly one terminal tool. Map Sauti's portable
            // end_call name to the provider-native hangup below rather than
            // requiring the model to execute a webhook and a second tool.
            if ("end_call".equals(tool.name())) return;
            var webhook = new LinkedHashMap<String, Object>();
            webhook.put("name", tool.name());
            var description = tool.description() == null ? "" : tool.description();
            if (tool.callerWaitExpected()) {
                description += " This operation can make the caller wait. Unless a progress acknowledgment was "
                        + "already spoken after the caller's latest turn, immediately before invoking it say exactly "
                        + "one natural sentence, ideally under eight words, in the caller's current language. It "
                        + "should communicate only that you are " + progressPurpose(tool.name()) + ". Do not ask a "
                        + "question or imply success or failure. Do not add another progress sentence for an "
                        + "immediately chained tool. After the result returns, continue automatically and explain "
                        + "only the factual outcome.";
            }
            if ("reschedule_booking".equals(tool.name())) {
                description += " Invoke this tool with confirmation_state=not_confirmed to retain and review the "
                        + "proposed change. On the caller's later answer, do not invoke this tool directly and do not "
                        + "demand a fixed phrase. Send the complete answer to update_conversation_state; Sauti will "
                        + "semantically authorize and invoke the retained reschedule when appropriate.";
            }
            webhook.put("description", description.trim());
            webhook.put(
                    "url",
                    publicBaseUrl + "/webhooks/telnyx/tools/" + path(tool.name())
                            + "?callSid={{sauti_call_sid}}"
            );
            webhook.put("method", "POST");
            // Sauti keeps factual CRUD results synchronous so Telnyx resumes the
            // same turn with the authoritative response. Telnyx otherwise uses
            // a roughly five-second default, which is too short for a guarded
            // calendar or integration operation.
            webhook.put("async", false);
            webhook.put("timeout_ms", 30_000);
            webhook.put("headers", java.util.List.of(
                    Map.of("name", "x-sauti-tool-secret", "value", toolWebhookSecret)
            ));
            webhook.put("body_parameters", tool.inputSchema());
            tools.add(Map.of(
                    "type", "webhook",
                    "webhook", Map.copyOf(webhook)
            ));
        });
        tools.add(Map.of(
                "type", "hangup",
                "hangup", Map.of(
                        "description", "For phone_call conversations only: when the caller clearly indicates they "
                                + "are finished, first speak one complete farewell of no more than six words. Invoke "
                                + "this tool only after the final word has finished playing. Never invoke it while "
                                + "farewell speech is still being generated or played."
                )
        ));
        tools.add(Map.of(
                "type", "client_side_tool",
                "client_side_tool", Map.of(
                        "name", "end_browser_call",
                        "description", "For web_call conversations only: when the caller clearly indicates they are "
                                + "finished, first speak one complete farewell of no more than six words. Invoke this "
                                + "tool only after the final word has finished playing. Never invoke it while farewell "
                                + "speech is still being generated or played. Speaking the farewell without invoking "
                                + "this tool does not end the conversation.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", java.util.List.of()
                        )
                )
        ));
        var body = new LinkedHashMap<String, Object>();
        body.put("name", shorten(blueprint.name(), 100));
        body.put("instructions", blueprint.instructions() + """

                TELNYX EXECUTION CONTRACT:
                - Call a required business tool before speaking about its result.
                - requestProcessed=true means Sauti handled the tool request. For mutations, success,
                  mutationCompleted, and actionPerformed must all be true before saying the requested change happened.
                  completionStatus=not_completed means it did not happen, including when workflowPending=true.
                - Treat the returned result as authoritative. For any mutation, claim success only when the result
                  explicitly contains actionPerformed=true.
                - A reschedule is never a conversational promise. Look up the booking, verify availability, invoke
                  reschedule_booking, and say it was rescheduled only from a result where actionPerformed=true and
                  status=booking_rescheduled. If reschedule_booking was not invoked, the booking is unchanged.
                - Tool data is language-neutral. When responseMode is present, render its structured data naturally
                  in the caller's current language and locale without changing stored names, references, or values.
                  Never expect or request a finite server-side translation.
                - If check_availability returns status=calendar_temporarily_unavailable or
                  responseMode=render_calendar_unavailable, availability is unknown rather than unavailable. Keep the
                  requested date and time unchanged, do not offer any other date or time, and ask once whether the
                  caller wants the exact same lookup retried. Opening hours never prove that an appointment is free.
                - Treat update_conversation_state as the required semantic boundary whenever a caller turn supplies
                  or corrects customer facts, changes booking intent, authorizes a business action, or decides a signed
                  review. Persist every newly completed or corrected fact there; never acknowledge a value only in
                  conversational memory. Do not invoke it for a greeting, a request to repeat, or a static question
                  that neither changes authoritative state nor authorizes an action. A name introduction without an
                  actual person-name entity is incomplete: store no name, ask the caller to continue, and replace any
                  earlier partial value when the caller supplies the complete name. The only exception is a clean,
                  unconditional answer to the latest signed booking review, which may use the direct review transition
                  described below.
                - Every person-name tool field is structured data, not a transcript field. Semantically extract only
                  the complete person-name entity in whatever language the caller used. Never copy an introduction,
                  carrier phrase, complete answer, or surrounding sentence into caller_name or appointment_name.
                - On every update_conversation_state call, copy the exact latest caller transcript verbatim into
                  source_utterance. Never translate, normalize, summarize, correct, repunctuate, or omit repeated
                  fragments from source_utterance. Sauti uses it as server-side evidence for exact entity extraction.
                  Set phone_target to caller_phone or new_caller_phone whenever that utterance attempts to supply or
                  correct that field, even when you believe the sequence is incomplete; otherwise set it to
                  not_applicable. Sauti, not conversational memory, determines the authoritative digits.
                - A phone number is complete only when every digit in the caller's finished sequence is unambiguous.
                  If transcription contains an unclear sound, missing digit, interruption, or unfinished sequence,
                  store no phone update and ask for one slow natural repetition. Never reconstruct uncertain sounds
                  into plausible digits. During the signed booking review, read every stored digit individually in
                  the caller's language. When a tool result contains callerPhoneDigits, reproduce each array element
                  exactly once and in its original order. Never regenerate the number from conversational memory,
                  duplicate a digit, omit a digit, or insert a separator as a digit. A caller correction is
                  correct_review, never approval of the old review; produce and obtain approval of the focused
                  correction review before saving.
                - Interpret approval, correction, rejection, and negation semantically from the complete caller turn
                  and the immediately preceding question, in any language. Do not classify intent from a language
                  keyword list. For a signed booking review, you may record a clean correction or unconditional
                  approval directly with book_slot using review_action=correct_review or approve_review and
                  question_handling=ready_for_action. If the turn also contains a question, condition, hesitation,
                  rejection, or correction alongside approval, use update_conversation_state instead and do not save.
                  booking_review_decision_required is a pending workflow result, never a technical failure.
                - workflowPending=true or actionPerformed=false is a valid workflow step, not a tool failure. Follow
                  instruction, nextTool, nextToolArguments, and nextToolAuthorized exactly. Do not retry the same
                  mutation merely because nothing changed yet.
                - For a mutation, success=false means the requested change was not completed. When
                  requestProcessed=true, follow the returned workflow instruction; otherwise explain only that the
                  tool failed. Never describe the requested mutation as completed.
                - A later response to a retained action confirmation must always be interpreted through
                  update_conversation_state with the exact source_utterance. For a clear unconditional approval, set
                  review_decision=approved, action_authorization=unconditional, and caller_question=none. Sauti will
                  invoke the exact retained mutation automatically. Do not call the mutation tool directly, do not
                  ask repeatedly, and never demand a language-specific or fixed phrase such as "I confirm".
                - For SMS or WhatsApp on a real phone call, ask whether the caller wants the message sent to the
                  number they are calling from. After clear consent, omit the phone argument so Sauti uses the
                  provider-verified calling number; never make the caller repeat that number. In a browser call,
                  where caller ID is unavailable, collect the complete destination once. A local number may omit
                  the country code; a number from another country must include it. WhatsApp requires explicit opt-in.
                - For a tool marked as potentially slow, cover the wait with exactly one brief progress sentence in
                  the caller's current language immediately before invoking it. Use no more than one progress sentence
                  after each caller turn, even when tools are chained. Never ask a question or imply success.
                - After every tool result, continue automatically in the same turn; never wait for more caller speech.
                - Keep each spoken answer continuous and concise.
                - On Telnyx, do not call the portable end_call webhook. Use Sauti's explicit conversation channel
                  `{{sauti_conversation_channel}}` to select exactly one terminal action. First speak one complete,
                  natural farewell of no more than six words. Wait until its final word has completely finished
                  playing, then invoke `end_browser_call` for `web_call` or Telnyx's native `hangup` for `phone_call`.
                  Never invoke a terminal tool in parallel with generated or playing speech. Do not pass
                  spoken_farewell, outcome, or summary arguments, do not call a webhook first, and never wait for
                  another caller turn after the farewell. A spoken farewell alone is not a terminal action: invoke
                  the selected terminal tool immediately after farewell playback completes.
                """);
        body.put("greeting", blueprint.greeting());
        body.put("model", aiModel);
        // Telnyx currently reports AI Agent SDK WebRTC sessions as phone_call.
        // Keep phone calls as the safe default and let the browser override this
        // variable through X-Sauti-Conversation-Channel at conversation start.
        body.put("dynamic_variables", Map.of("sauti_conversation_channel", "phone_call"));
        body.put("voice_settings", Map.of(
                "voice",
                TelnyxVoiceCompatibility.select(
                        blueprint.voiceId(),
                        blueprint.language(),
                        defaultVoiceId
                )
        ));
        var transcription = new LinkedHashMap<String, Object>();
        transcription.put("model", "deepgram/nova-3");
        // Every prepared Telnyx assistant already represents one configured
        // opening language. Give Nova-3 that explicit prior instead of broad
        // auto-detection, which misclassified short accented English turns.
        transcription.put("language", transcriptionLanguage(blueprint));
        var transcriptionSettings = new LinkedHashMap<String, Object>();
        transcriptionSettings.put("smart_format", true);
        transcriptionSettings.put("numerals", true);
        var keyterms = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("SAT", "Sauti"),
                        blueprint.boostedKeywords().stream()
                )
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.replace(",", " "))
                .distinct()
                .limit(100)
                .toList();
        transcriptionSettings.put("keyterm", String.join(",", keyterms));
        transcription.put("settings", Map.copyOf(transcriptionSettings));
        body.put("transcription", Map.copyOf(transcription));
        body.put("tools", tools);
        body.put("enabled_features", java.util.List.of("telephony"));
        body.put("telephony_settings", Map.of(
                "supports_unauthenticated_web_calls", true,
                "time_limit_secs", Math.max(10, blueprint.maxCallDurationSeconds()),
                "user_idle_timeout_secs", 60,
                "recording_settings", Map.of(
                        "enabled", true,
                        "channels", "dual",
                        "format", "mp3",
                        "stop_on_conversation_end", true
                )
        ));
        // Telnyx validation 10015 requires provider data retention whenever
        // assistant-managed recording is enabled.
        body.put("privacy_settings", Map.of("data_retention", true));
        body.put("interruption_settings", Map.of(
                "enable", true,
                // Protect the complete opening sentence. Browser microphone
                // activation and background noise can otherwise trigger VAD
                // while Telnyx is beginning the greeting and clip its first words.
                "disable_greeting_interruption", true,
                "start_speaking_plan", Map.of(
                        // Nova-3 does not provide Flux's semantic end-of-turn
                        // detection. Give multilingual callers enough time for
                        // a natural pause so introductions such as "my name is
                        // ... Zacari" are not split before the actual entity.
                        "wait_seconds", 0.15,
                        "transcription_endpointing_plan", Map.of(
                                "on_punctuation_seconds", 0.1,
                                "on_no_punctuation_seconds", Math.max(
                                        0.9,
                                        Math.min(1.2, blueprint.endpointingMilliseconds() / 1000.0)
                                ),
                                // Numbers retain a longer pause budget so a
                                // natural phone-number grouping is not cut off.
                                "on_number_seconds", 1.0
                        )
                )
        ));
        if (updating) body.put("promote_to_main", true);
        body.put("tags", java.util.List.of("sauti-managed"));
        return Map.copyOf(body);
    }

    private static String progressPurpose(String toolName) {
        return switch (toolName) {
            case "check_availability" -> "checking the requested time";
            case "lookup_booking" -> "finding the booking";
            case "book_slot" -> "preparing or saving the booking";
            case "update_booking" -> "updating the booking details";
            case "reschedule_booking" -> "checking or applying the requested change";
            case "cancel_booking" -> "processing the cancellation request";
            case "transfer_to_human" -> "connecting the caller with the team";
            case "send_confirmation_sms", "send_whatsapp_message" -> "sending the requested message";
            case "lookup_google_sheet_row" -> "checking the customer record";
            case "update_google_sheet_row" -> "updating the customer record";
            case "request_mpesa_payment", "check_mpesa_payment" -> "checking the payment request";
            default -> "working on the request";
        };
    }

    private static String path(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String transcriptionLanguage(ManagedVoiceAgentBlueprint blueprint) {
        var primary = trim(blueprint.language()).toLowerCase(java.util.Locale.ROOT);
        return primary.isBlank() ? "en" : primary;
    }

    private static String shorten(String value, int maximum) {
        var normalized = trim(value);
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        var normalized = trim(value);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
