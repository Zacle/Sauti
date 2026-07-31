package com.sauti.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tool.CalendarCredential;
import com.sauti.tool.CalendarCredentialRepository;
import com.sauti.tool.CredentialEncryption;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarApiClient {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration BUSY_CACHE_TTL = Duration.ofSeconds(3);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper;
    private final CredentialEncryption encryption;
    private final CalendarCredentialRepository credentialRepository;
    private final String clientId;
    private final String clientSecret;
    private final ConcurrentHashMap<BusyCacheKey, BusyCacheEntry> busyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BusyCacheKey, CompletableFuture<List<BusyPeriod>>> busyRequests =
            new ConcurrentHashMap<>();

    public GoogleCalendarApiClient(
            ObjectMapper objectMapper,
            CredentialEncryption encryption,
            CalendarCredentialRepository credentialRepository,
            @Value("${sauti.calendar.google.client-id:}") String clientId,
            @Value("${sauti.calendar.google.client-secret:}") String clientSecret
    ) {
        this.objectMapper = objectMapper;
        this.encryption = encryption;
        this.credentialRepository = credentialRepository;
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
    }

    public List<BusyPeriod> busy(CalendarCredential credential, OffsetDateTime from, OffsetDateTime to, String timezone) {
        var calendarId = calendarId(credential);
        var key = new BusyCacheKey(credential.getId(), calendarId, from, to, timezone);
        var now = System.nanoTime();
        busyCache.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
        var cached = busyCache.get(key);
        if (cached != null) return cached.periods();

        var request = new CompletableFuture<List<BusyPeriod>>();
        var running = busyRequests.putIfAbsent(key, request);
        if (running != null) return awaitBusy(running);
        try {
            var periods = fetchBusy(credential, calendarId, from, to, timezone);
            busyCache.put(key, new BusyCacheEntry(
                    periods,
                    System.nanoTime() + BUSY_CACHE_TTL.toNanos()
            ));
            request.complete(periods);
            return periods;
        } catch (RuntimeException exception) {
            request.completeExceptionally(exception);
            throw exception;
        } finally {
            busyRequests.remove(key, request);
        }
    }

    private List<BusyPeriod> fetchBusy(
            CalendarCredential credential,
            String calendarId,
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone
    ) {
        var body = objectMapper.createObjectNode()
                .put("timeMin", from.toString())
                .put("timeMax", to.toString())
                .put("timeZone", timezone);
        body.putArray("items").addObject().put("id", calendarId);
        var response = send(credential, HttpRequest.newBuilder(URI.create(CALENDAR_API + "/freeBusy"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        try {
            var busy = objectMapper.readTree(response).path("calendars").path(calendarId).path("busy");
            var periods = new java.util.ArrayList<BusyPeriod>();
            busy.forEach(node -> periods.add(new BusyPeriod(
                    OffsetDateTime.parse(node.path("start").asText()),
                    OffsetDateTime.parse(node.path("end").asText())
            )));
            return List.copyOf(periods);
        } catch (Exception exception) {
            throw new IllegalStateException("Google Calendar availability response was invalid", exception);
        }
    }

    public String createEvent(CalendarCredential credential, Booking booking) {
        invalidateBusyCache(credential);
        var start = booking.getAppointmentAt();
        var end = start.plusMinutes(booking.getDurationMinutes());
        var eventId = "sauti" + booking.getId().toString()
                .replace("-", "")
                .toLowerCase(java.util.Locale.ROOT);
        var body = objectMapper.createObjectNode()
                .put("id", eventId)
                .put("summary", booking.getServiceType() + " — " + booking.getCallerName())
                .put("description", eventDescription(booking));
        body.putObject("start").put("dateTime", start.toString()).put("timeZone", booking.getAgent().getTimezone());
        body.putObject("end").put("dateTime", end.toString()).put("timeZone", booking.getAgent().getTimezone());
        var endpoint = CALENDAR_API + "/calendars/" + encode(calendarId(credential))
                + "/events/" + encode(eventId);
        final String response;
        try {
            response = send(credential, HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString())));
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("status 409")) {
                return eventId;
            }
            throw exception;
        }
        try {
            var id = objectMapper.readTree(response).path("id").asText("");
            if (id.isBlank()) throw new IllegalStateException("Google Calendar did not return an event ID");
            return id;
        } catch (Exception exception) {
            throw new IllegalStateException("Google Calendar event response was invalid", exception);
        }
    }

    public void updateEvent(CalendarCredential credential, Booking booking) {
        invalidateBusyCache(credential);
        if (booking.getExternalEventId() == null || booking.getExternalEventId().isBlank()) {
            throw new IllegalArgumentException("Booking is not linked to a Google Calendar event");
        }
        var start = booking.getAppointmentAt();
        var end = start.plusMinutes(booking.getDurationMinutes());
        var body = objectMapper.createObjectNode()
                .put("summary", booking.getServiceType() + " — " + booking.getCallerName())
                .put("description", eventDescription(booking));
        body.putObject("start").put("dateTime", start.toString()).put("timeZone", booking.getAgent().getTimezone());
        body.putObject("end").put("dateTime", end.toString()).put("timeZone", booking.getAgent().getTimezone());
        var endpoint = CALENDAR_API + "/calendars/" + encode(calendarId(credential))
                + "/events/" + encode(booking.getExternalEventId());
        send(credential, HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    public void deleteEvent(CalendarCredential credential, String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        invalidateBusyCache(credential);
        var endpoint = CALENDAR_API + "/calendars/" + encode(calendarId(credential)) + "/events/" + encode(eventId);
        send(credential, HttpRequest.newBuilder(URI.create(endpoint)).DELETE(), java.util.Set.of(404));
    }

    public void test(CalendarCredential credential, String timezone) {
        var now = OffsetDateTime.now();
        busy(credential, now, now.plusDays(1), timezone);
    }

    private String send(CalendarCredential credential, HttpRequest.Builder builder) {
        return send(credential, builder, java.util.Set.of());
    }

    private String send(
            CalendarCredential credential,
            HttpRequest.Builder builder,
            java.util.Set<Integer> acceptedStatuses
    ) {
        try {
            var request = builder.timeout(REQUEST_TIMEOUT)
                    .setHeader("Authorization", "Bearer " + accessToken(credential)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                request = builder.timeout(REQUEST_TIMEOUT)
                        .setHeader("Authorization", "Bearer " + refreshAccessToken(credential)).build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() / 100 != 2 && !acceptedStatuses.contains(response.statusCode())) {
                throw new IllegalStateException("Google Calendar request failed with status " + response.statusCode());
            }
            return response.body();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Google Calendar could not be reached", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Calendar request was interrupted", exception);
        }
    }

    private String eventDescription(Booking booking) {
        return "Managed by Sauti. Update or cancel this booking in Sauti to keep systems synchronized."
                + "\nBooking: " + booking.getBookingReference()
                + "\nCaller: " + booking.getCallerPhone();
    }

    private List<BusyPeriod> awaitBusy(CompletableFuture<List<BusyPeriod>> request) {
        try {
            return request.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw exception;
        }
    }

    private void invalidateBusyCache(CalendarCredential credential) {
        var credentialId = credential.getId();
        busyCache.keySet().removeIf(key -> key.credentialId().equals(credentialId));
    }

    private synchronized String accessToken(CalendarCredential credential) {
        if (credential.getTokenExpiry() == null || credential.getTokenExpiry().isAfter(OffsetDateTime.now().plusSeconds(60))) {
            return encryption.decrypt(credential.getAccessToken());
        }
        return refreshAccessToken(credential);
    }

    private synchronized String refreshAccessToken(CalendarCredential credential) {
        var refreshToken = encryption.decrypt(credential.getRefreshToken());
        if (refreshToken.isBlank()) throw new IllegalStateException("Reconnect Google Calendar to renew access");
        var body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
        try {
            var response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(TOKEN_URL))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Google Calendar token refresh failed");
            }
            var token = objectMapper.readTree(response.body());
            var accessToken = token.path("access_token").asText("");
            credential.updateTokens(
                    encryption.encrypt(accessToken),
                    null,
                    OffsetDateTime.now().plusSeconds(token.path("expires_in").asLong(3600))
            );
            credentialRepository.save(credential);
            return accessToken;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Google Calendar token refresh could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Calendar token refresh was interrupted", exception);
        }
    }

    private String calendarId(CalendarCredential credential) {
        return credential.getExternalId() == null || credential.getExternalId().isBlank()
                ? "primary"
                : credential.getExternalId();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record BusyPeriod(OffsetDateTime start, OffsetDateTime end) {
    }

    private record BusyCacheKey(
            UUID credentialId,
            String calendarId,
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone
    ) {
    }

    private record BusyCacheEntry(List<BusyPeriod> periods, long expiresAtNanos) {
    }
}
