package com.sauti.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.calendar.Booking;
import com.sauti.calendar.BookingIdentityService;
import com.sauti.calendar.BookingRepository;
import com.sauti.call.Call;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GoogleSheetsCustomerSyncService {
    private final BookingRepository bookings;
    private final GoogleSheetsApiClient googleSheets;

    public GoogleSheetsCustomerSyncService(BookingRepository bookings, GoogleSheetsApiClient googleSheets) {
        this.bookings = bookings;
        this.googleSheets = googleSheets;
    }

    public void syncConfirmedBookingCustomer(Call call, Map<String, Object> configuration) {
        var booking = bookings.findFirstByTenantIdAndCall_IdAndAgent_Id(
                        call.getTenant().getId(), call.getId(), call.getAgent().getId()
                )
                .filter(item -> "confirmed".equals(item.getStatus()))
                .orElse(null);
        if (booking == null) return;

        var spreadsheetId = required(configuration, "spreadsheetId");
        var range = required(configuration, "range");
        var phoneColumn = index(configuration, "lookupColumn", 0);
        var nameColumn = index(configuration, "customerNameColumn", 1);
        var emailColumn = index(configuration, "customerEmailColumn", 2);
        var width = Math.max(phoneColumn, Math.max(nameColumn, emailColumn)) + 1;
        var rows = googleSheets.values(
                call.getTenant().getId(), call.getAgent().getId(), spreadsheetId, range
        ).path("values");
        var normalizedPhone = BookingIdentityService.normalizePhone(booking.getCallerPhone());

        for (int index = 0; index < rows.size(); index++) {
            var row = rows.path(index);
            if (!samePhone(normalizedPhone, row.path(phoneColumn).asText(""))) continue;
            var rowNumber = firstRow(range) + index;
            if (row.path(nameColumn).asText("").isBlank() && !text(booking.getCallerName()).isBlank()) {
                googleSheets.updateValues(
                        call.getTenant().getId(), call.getAgent().getId(), spreadsheetId,
                        cellRange(range, rowNumber, nameColumn), List.of(booking.getCallerName().trim())
                );
            }
            if (row.path(emailColumn).asText("").isBlank() && !text(booking.getCallerEmail()).isBlank()) {
                googleSheets.updateValues(
                        call.getTenant().getId(), call.getAgent().getId(), spreadsheetId,
                        cellRange(range, rowNumber, emailColumn), List.of(booking.getCallerEmail().trim())
                );
            }
            return;
        }

        var customer = blankRow(width);
        customer.set(phoneColumn, booking.getCallerPhone());
        customer.set(nameColumn, booking.getCallerName());
        customer.set(emailColumn, text(booking.getCallerEmail()));
        googleSheets.appendValues(
                call.getTenant().getId(), call.getAgent().getId(), spreadsheetId, range, customer
        );
    }

    private static boolean samePhone(String normalizedExpected, String candidate) {
        return !normalizedExpected.isBlank()
                && normalizedExpected.equals(BookingIdentityService.normalizePhone(candidate));
    }

    private static List<String> blankRow(int width) {
        var values = new ArrayList<String>(width);
        for (int index = 0; index < width; index++) values.add("");
        return values;
    }

    private static int index(Map<String, Object> configuration, String key, int fallback) {
        try {
            var value = String.valueOf(configuration.getOrDefault(key, fallback)).trim();
            var parsed = Integer.parseInt(value);
            return parsed < 0 ? fallback : parsed;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int firstRow(String range) {
        var match = java.util.regex.Pattern.compile("![A-Za-z]+(\\d+)").matcher(range);
        return match.find() ? Integer.parseInt(match.group(1)) : 1;
    }

    private static String cellRange(String range, int row, int relativeColumn) {
        var sheet = range.contains("!") ? range.substring(0, range.indexOf('!') + 1) : "";
        var coordinates = range.contains("!") ? range.substring(range.indexOf('!') + 1) : range;
        var match = java.util.regex.Pattern.compile("\\$?([A-Za-z]+)").matcher(coordinates);
        if (!match.find()) throw new IllegalArgumentException("Configured customer range must use A1 notation");
        var start = match.group(1).toUpperCase(java.util.Locale.ROOT);
        return sheet + offsetColumn(start, relativeColumn) + row;
    }

    private static String offsetColumn(String column, int offset) {
        var number = 0;
        for (int index = 0; index < column.length(); index++) {
            number = number * 26 + column.charAt(index) - 'A' + 1;
        }
        number += offset;
        var result = new StringBuilder();
        while (number > 0) {
            number--;
            result.insert(0, (char) ('A' + number % 26));
            number /= 26;
        }
        return result.toString();
    }

    private static String required(Map<String, Object> configuration, String key) {
        var value = text(configuration.get(key));
        if (value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
