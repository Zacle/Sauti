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
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarApiClient {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final CredentialEncryption encryption;
    private final CalendarCredentialRepository credentialRepository;
    private final String clientId;
    private final String clientSecret;

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
        var start = booking.getAppointmentAt();
        var end = start.plusHours(1);
        var body = objectMapper.createObjectNode()
                .put("summary", booking.getServiceType() + " — " + booking.getCallerName())
                .put("description", "Booked by Sauti. Caller: " + booking.getCallerPhone());
        body.putObject("start").put("dateTime", start.toString()).put("timeZone", booking.getAgent().getTimezone());
        body.putObject("end").put("dateTime", end.toString()).put("timeZone", booking.getAgent().getTimezone());
        var endpoint = CALENDAR_API + "/calendars/" + encode(calendarId(credential)) + "/events";
        var response = send(credential, HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        try {
            var id = objectMapper.readTree(response).path("id").asText("");
            if (id.isBlank()) throw new IllegalStateException("Google Calendar did not return an event ID");
            return id;
        } catch (Exception exception) {
            throw new IllegalStateException("Google Calendar event response was invalid", exception);
        }
    }

    private String send(CalendarCredential credential, HttpRequest.Builder builder) {
        try {
            var request = builder.header("Authorization", "Bearer " + accessToken(credential)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
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

    private synchronized String accessToken(CalendarCredential credential) {
        if (credential.getTokenExpiry() == null || credential.getTokenExpiry().isAfter(OffsetDateTime.now().plusSeconds(60))) {
            return encryption.decrypt(credential.getAccessToken());
        }
        var refreshToken = encryption.decrypt(credential.getRefreshToken());
        if (refreshToken.isBlank()) throw new IllegalStateException("Reconnect Google Calendar to renew access");
        var body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
        try {
            var response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(TOKEN_URL))
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
}
