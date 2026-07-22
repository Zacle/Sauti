package com.sauti.llm;

import com.sauti.session.BookingDraft;
import com.sauti.session.CallSessionStore;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.llm.provider", havingValue = "heuristic", matchIfMissing = true)
public class LocalToolCallingLlmProvider implements LlmToolCallingProvider {
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)\\b(?:my name is|i am|i'm)\\s+([a-z][a-z\\-']{1,40})\\b");
    private final CallSessionStore callSessionStore;

    public LocalToolCallingLlmProvider(CallSessionStore callSessionStore) {
        this.callSessionStore = callSessionStore;
    }

    @Override
    public boolean requiresAvailabilityFollowUpForState() {
        return true;
    }

    @Override
    public boolean supportsSemanticTurnTool() {
        return false;
    }

    @Override
    public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
        if (hasSuccessfulTool(context, "transfer_to_human")) {
            return new LlmToolTurnResponse(transferResponse(context.language(), context.agent().humanTransferNumber()), List.of());
        }
        if (hasSuccessfulTool(context, "book_slot")) {
            return new LlmToolTurnResponse(localized(
                    context.language(),
                    "Perfect, you're all booked!",
                    "Parfait, c'est confirme !",
                    "Vizuri, umewekwa kwenye ratiba!",
                    "رائع، تم حجز موعدك!"
            ), List.of());
        }
        var bookingReview = latestBookingReview(context);
        if (bookingReview != null) {
            var fields = bookingReview.result().get("bookingReview") instanceof Map<?, ?> values
                    ? values : Map.of();
            callSessionStore.updatePendingBooking(context.callSid(), new BookingDraft(
                    field(fields, "callerName", "Caller"),
                    field(fields, "service", "Appointment"),
                    "",
                    field(fields, "appointmentAt", ""),
                    field(fields, "callerPhone", context.callerPhone()),
                    true,
                    bookingReview.result().get("reviewToken").toString(),
                    integerField(fields, "durationMinutes", 60)
            ));
            return new LlmToolTurnResponse(bookingReview.result().get("spokenResponse").toString(), List.of());
        }
        if (hasSuccessfulTool(context, "end_call")) {
            return new LlmToolTurnResponse(localized(
                    context.language(),
                    "Great, take care!",
                    "Tres bien, bonne journee !",
                    "Sawa, jiangalie!",
                    "حسناً، إلى اللقاء!"
            ), List.of());
        }

        var transcript = context.callerTranscript().toLowerCase(Locale.ROOT);
        if (isConfirmation(transcript)) {
            var pendingBooking = callSessionStore.pendingBooking(context.callSid()).orElse(null);
            if (pendingBooking != null) {
                if (!pendingBooking.identityReadbackRequested()) {
                    callSessionStore.updatePendingBooking(context.callSid(), new BookingDraft(
                            pendingBooking.callerName(), pendingBooking.serviceType(), pendingBooking.preferredDate(),
                            pendingBooking.confirmedSlot(), pendingBooking.callerPhone(), true,
                            pendingBooking.reviewToken(), pendingBooking.durationMinutes()
                    ));
                    var spelling = natoSpelling(pendingBooking.callerName());
                    var digits = individualDigits(pendingBooking.callerPhone());
                    return new LlmToolTurnResponse(localized(
                            context.language(),
                            "Before I book, please confirm the spelling of your name: " + spelling + ", and your phone number: " + digits + ". Is that correct?",
                            "Avant de reserver, confirmez l'epellation de votre nom : " + spelling + ", et votre numero : " + digits + ". Est-ce correct ?",
                            "Kabla ya kuweka nafasi, thibitisha tahajia ya jina lako: " + spelling + ", na nambari yako: " + digits + ". Je, ni sahihi?",
                            "Before I book, please confirm the spelling of your name: " + spelling + ", and your phone number: " + digits + ". Is that correct?"
                    ), List.of());
                }
                return new LlmToolTurnResponse(localized(
                        context.language(),
                        "I will confirm that appointment now.",
                        "Je confirme ce rendez-vous maintenant.",
                        "Nitathibitisha miadi hiyo sasa.",
                        "سأؤكد هذا الموعد الآن."
                ), List.of(
                        tool("book_slot", bookingArguments(pendingBooking))
                ));
            }
        }
        if (isEscalationRequest(context, transcript)) {
            return new LlmToolTurnResponse("", List.of(tool("transfer_to_human", Map.of("reason", "caller requested human"))));
        }
        if (transcript.contains("voicemail") || transcript.contains("leave a message")) {
            return new LlmToolTurnResponse(
                    localized(
                            context.language(),
                            "I can take a message. Please tell me what you would like to share.",
                            "Je peux prendre votre message. Dites-moi ce que vous souhaitez transmettre.",
                            "Ninaweza kuchukua ujumbe. Tafadhali niambie ungependa kusema nini.",
                            "يمكنني تدوين رسالة. أخبرني بما تود مشاركته."
                    ),
                    List.of(tool("end_call", Map.of("outcome", "voicemail", "summary", "Caller requested voicemail")))
            );
        }
        if (context.agent().bookingEnabled() && isBookingRequest(transcript)) {
            var appointmentAt = inferAppointmentTime(transcript, context.agent().timezone());
            if (appointmentAt == null) {
                return new LlmToolTurnResponse(
                        localized(
                                context.language(),
                                "Of course. What date and time would you like for the booking?",
                                "Bien sur. Quelle date et heure souhaitez-vous pour le rendez-vous ?",
                                "Sawa. Ungependa miadi iwe tarehe na saa gani?",
                                "بالتأكيد. ما التاريخ والوقت المناسبان للموعد؟"
                        ),
                        List.of()
                );
            }
            var availability = latestSuccessfulResult(context, "check_availability");
            if (availability == null) {
                return new LlmToolTurnResponse(localized(
                        context.language(),
                        "One moment while I check availability.",
                        "Un instant pendant que je verifie les disponibilites.",
                        "Subiri kidogo ninapoangalia nafasi zilizopo.",
                        "لحظة من فضلك بينما أتحقق من الأوقات المتاحة."
                ), List.of(
                        tool("check_availability", Map.of(
                                "date", appointmentAt.toLocalDate().toString(),
                                "time_preference", appointmentAt.toLocalTime().toString(),
                                "duration_minutes", 60,
                                "timezone", context.agent().timezone()
                        ))
                ));
            }
            var selectedSlot = firstAvailableSlot(availability, appointmentAt);
            callSessionStore.updatePendingBooking(context.callSid(), new BookingDraft(
                    inferName(context.callerTranscript()),
                    inferService(transcript),
                    appointmentAt.toLocalDate().toString(),
                    selectedSlot,
                    context.callerPhone()
            ));
            return new LlmToolTurnResponse(localized(
                    context.language(),
                    "I found an available slot at " + selectedSlot + ". Would you like me to confirm it?",
                    "J'ai une disponibilite a " + selectedSlot + ". Voulez-vous confirmer ce rendez-vous ?",
                    "Nimepata nafasi saa " + selectedSlot + ". Ungependa niithibitishe?",
                    "وجدت موعدا متاحا في " + selectedSlot + ". هل تريد تأكيده؟"
            ),
                    List.of()
            );
        }

        var response = localized(
                context.language(),
                "Thanks. I am " + context.agent().name() + ". How can I help today?",
                "Merci. Je suis " + context.agent().name() + ". Comment puis-je vous aider aujourd'hui ?",
                "Asante. Mimi ni " + context.agent().name() + ". Ninaweza kukusaidiaje leo?",
                "شكرا. أنا " + context.agent().name() + ". كيف يمكنني مساعدتك اليوم؟"
        );
        return new LlmToolTurnResponse(response, List.of());
    }

    private boolean hasSuccessfulTool(LlmToolTurnContext context, String name) {
        return context.toolResults().stream().anyMatch(result -> {
            if (!name.equals(result.name()) || !result.success()) return false;
            if ("book_slot".equals(name)) return Boolean.TRUE.equals(result.result().get("bookingCreated"));
            return true;
        });
    }

    private LlmToolResult latestSuccessfulResult(LlmToolTurnContext context, String name) {
        return context.toolResults().stream()
                .filter(result -> name.equals(result.name()) && result.success())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private LlmToolResult latestBookingReview(LlmToolTurnContext context) {
        return context.toolResults().stream()
                .filter(result -> "book_slot".equals(result.name()) && result.success())
                .filter(result -> "booking_review_required".equals(result.result().get("status")))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private Map<String, Object> bookingArguments(BookingDraft booking) {
        var arguments = new java.util.LinkedHashMap<String, Object>();
        arguments.put("appointment_at", booking.confirmedSlot());
        arguments.put("caller_name", booking.callerName());
        arguments.put("caller_phone", booking.callerPhone());
        arguments.put("service_type", booking.serviceType());
        arguments.put("duration_minutes", booking.durationMinutes() > 0 ? booking.durationMinutes() : 60);
        if (booking.reviewToken() != null && !booking.reviewToken().isBlank()) {
            arguments.put("review_token", booking.reviewToken());
            arguments.put("review_action", "approve_review");
        } else {
            arguments.put("review_action", "prepare_review");
        }
        return Map.copyOf(arguments);
    }

    private String field(Map<?, ?> fields, String key, String fallback) {
        var value = fields.get(key);
        var safeFallback = fallback == null ? "" : fallback;
        return value == null || value.toString().isBlank() ? safeFallback : value.toString();
    }

    private int integerField(Map<?, ?> fields, String key, int fallback) {
        try {
            var value = fields.get(key);
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String firstAvailableSlot(LlmToolResult availability, OffsetDateTime fallback) {
        var slots = availability.result().get("slots");
        if (slots instanceof List<?> slotList && !slotList.isEmpty() && slotList.get(0) instanceof Map<?, ?> slot) {
            var start = slot.get("start");
            if (start != null && !start.toString().isBlank()) {
                return start.toString();
            }
        }
        return fallback.toString();
    }

    private String individualDigits(String value) {
        if (value == null || value.isBlank()) return "not provided";
        return value.chars()
                .filter(Character::isDigit)
                .mapToObj(character -> Character.toString((char) character))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String natoSpelling(String value) {
        if (value == null || value.isBlank()) return "not provided";
        var alphabet = Map.ofEntries(
                Map.entry('A', "Alfa"), Map.entry('B', "Bravo"), Map.entry('C', "Charlie"),
                Map.entry('D', "Delta"), Map.entry('E', "Echo"), Map.entry('F', "Foxtrot"),
                Map.entry('G', "Golf"), Map.entry('H', "Hotel"), Map.entry('I', "India"),
                Map.entry('J', "Juliett"), Map.entry('K', "Kilo"), Map.entry('L', "Lima"),
                Map.entry('M', "Mike"), Map.entry('N', "November"), Map.entry('O', "Oscar"),
                Map.entry('P', "Papa"), Map.entry('Q', "Quebec"), Map.entry('R', "Romeo"),
                Map.entry('S', "Sierra"), Map.entry('T', "Tango"), Map.entry('U', "Uniform"),
                Map.entry('V', "Victor"), Map.entry('W', "Whiskey"), Map.entry('X', "X-ray"),
                Map.entry('Y', "Yankee"), Map.entry('Z', "Zulu")
        );
        return value.toUpperCase(Locale.ROOT).chars()
                .mapToObj(character -> {
                    if (Character.isWhitespace(character)) return "space";
                    var letter = (char) character;
                    return alphabet.getOrDefault(letter, Character.toString(letter));
                })
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private boolean isConfirmation(String transcript) {
        return transcript.contains("yes")
                || transcript.contains("confirm")
                || transcript.contains("go ahead")
                || transcript.contains("oui")
                || transcript.contains("confirme")
                || transcript.contains("ndiyo")
                || transcript.contains("thibitisha")
                || transcript.contains("نعم")
                || transcript.contains("أكد");
    }

    private boolean isEscalationRequest(LlmToolTurnContext context, String transcript) {
        return context.agent().escalationPhrases().stream()
                .map(phrase -> phrase.toLowerCase(Locale.ROOT))
                .anyMatch(transcript::contains);
    }

    private boolean isBookingRequest(String transcript) {
        return transcript.contains("book")
                || transcript.contains("appointment")
                || transcript.contains("schedule")
                || transcript.contains("reservation")
                || transcript.contains("rendez-vous")
                || transcript.contains("miadi")
                || transcript.contains("موعد")
                || transcript.contains("حجز");
    }

    private OffsetDateTime inferAppointmentTime(String transcript, String timezone) {
        var zone = ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone);
        var now = OffsetDateTime.now(zone).plusDays(1).withMinute(0).withSecond(0).withNano(0);
        if (transcript.contains("tomorrow") || transcript.contains("kesho") || transcript.contains("غدا")) {
            return now.withHour(10);
        }
        if (transcript.contains("next week")) {
            return now.plusWeeks(1).withHour(10);
        }
        return null;
    }

    private String inferName(String transcript) {
        var matcher = NAME_PATTERN.matcher(transcript);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Caller";
    }

    private String inferService(String transcript) {
        if (transcript.contains("consult")) {
            return "Consultation";
        }
        if (transcript.contains("demo")) {
            return "Demo";
        }
        return "Appointment";
    }

    private String transferResponse(String language, String transferNumber) {
        if (transferNumber == null || transferNumber.isBlank()) {
            return localized(
                    language,
                    "I cannot reach a human right now, but I can take a message.",
                    "Je ne peux pas joindre un humain pour le moment, mais je peux prendre un message.",
                    "Siwezi kumpata mhudumu kwa sasa, lakini ninaweza kuchukua ujumbe.",
                    "لا يمكنني الوصول إلى موظف الآن، لكن يمكنني تدوين رسالة."
            );
        }
        return localized(
                language,
                "I am transferring you to a team member.",
                "Je vous transfere vers un membre de l'equipe.",
                "Ninakuhamishia kwa mhudumu wa timu.",
                "سأحولك إلى أحد أعضاء الفريق."
        );
    }

    private String localized(String language, String english, String french, String swahili, String arabic) {
        return switch (language == null ? "" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> french;
            case "sw" -> swahili;
            case "ar" -> arabic;
            default -> english;
        };
    }

    private LlmToolCall tool(String name, Map<String, Object> arguments) {
        return new LlmToolCall(UUID.randomUUID().toString(), name, arguments);
    }

}
