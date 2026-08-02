package com.sauti.integration;

import com.sauti.calendar.Booking;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppTemplateParameterMapper {
    public List<Map<String, Object>> components(Map<String, Object> configuration, Booking booking) {
        var specifications = specifications(configuration.get("templateParameters"));
        if (specifications.isEmpty()) return List.of();
        var mappings = stringMap(configuration.get("templateParameterMappings"));
        var parameterFormat = text(configuration.get("templateParameterFormat"), "POSITIONAL");
        var grouped = new LinkedHashMap<String, List<ParameterSpecification>>();
        specifications.stream()
                .sorted(Comparator.comparingInt((ParameterSpecification specification) ->
                                componentOrder(specification.component()))
                        .thenComparingInt(ParameterSpecification::position))
                .forEach(specification -> grouped.computeIfAbsent(specification.component(), ignored -> new ArrayList<>())
                        .add(specification));

        var components = new ArrayList<Map<String, Object>>();
        grouped.forEach((component, values) -> {
            var parameters = values.stream().map(specification -> {
                var source = mappings.get(specification.key());
                if (source == null || source.isBlank()) {
                    throw new IllegalArgumentException("No Sauti field is mapped to WhatsApp placeholder "
                            + specification.key());
                }
                var parameter = new LinkedHashMap<String, Object>();
                parameter.put("type", "text");
                parameter.put("text", resolve(source, booking,
                        text(configuration.get("templateLanguage"), "en_US")));
                if ("NAMED".equalsIgnoreCase(parameterFormat)) {
                    parameter.put("parameter_name", specification.name());
                }
                return Map.copyOf(parameter);
            }).toList();
            components.add(Map.of("type", component, "parameters", parameters));
        });
        return List.copyOf(components);
    }

    private String resolve(String source, Booking booking, String language) {
        var locale = Locale.forLanguageTag(language.replace('_', '-'));
        var timezone = timezone(booking.getAgent().getTimezone());
        var appointment = booking.getAppointmentAt().atZoneSameInstant(timezone);
        var value = switch (source) {
            case "customer_name" -> booking.getCallerName();
            case "customer_phone" -> booking.getCallerPhone();
            case "customer_email" -> booking.getCallerEmail();
            case "service" -> booking.getServiceType();
            case "appointment_date" -> appointment.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
            case "appointment_time" -> appointment.format(
                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
            case "appointment_datetime" -> appointment.format(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale));
            case "booking_reference" -> booking.getBookingReference();
            case "business_name" -> booking.getTenant().getBusinessName();
            case "agent_name" -> booking.getAgent().getName();
            case "duration_minutes" -> Integer.toString(booking.getDurationMinutes());
            default -> throw new IllegalArgumentException("Unsupported WhatsApp template field: " + source);
        };
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WhatsApp template field " + source + " is empty for this booking");
        }
        return value;
    }

    private List<ParameterSpecification> specifications(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        var result = new ArrayList<ParameterSpecification>();
        for (var item : items) {
            if (!(item instanceof Map<?, ?> map)) continue;
            var key = text(map.get("key"), "");
            var component = text(map.get("component"), "").toLowerCase(Locale.ROOT);
            var name = text(map.get("name"), "");
            var position = integer(map.get("position"));
            if (key.isBlank() || !("header".equals(component) || "body".equals(component))
                    || name.isBlank() || position < 1) {
                throw new IllegalArgumentException("Stored WhatsApp template parameter metadata is invalid");
            }
            result.add(new ParameterSpecification(key, component, position, name));
        }
        return List.copyOf(result);
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        var result = new LinkedHashMap<String, String>();
        map.forEach((key, item) -> result.put(String.valueOf(key), text(item, "")));
        return Map.copyOf(result);
    }

    private ZoneId timezone(String value) {
        try {
            return ZoneId.of(value == null || value.isBlank() ? "UTC" : value);
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return 0; }
    }

    private int componentOrder(String component) {
        return "header".equals(component) ? 0 : 1;
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        var result = String.valueOf(value).trim();
        return result.isBlank() ? fallback : result;
    }

    private record ParameterSpecification(String key, String component, int position, String name) { }
}
