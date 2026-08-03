package com.sauti.admin;

import com.sauti.demo.DemoRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() { }

    public record Overview(long workspaces, long calls, long bookings, long customers,
                           long newDemoRequests, long invitedDemoRequests, long activatedPilots) { }

    public record DemoRequestItem(UUID id, String businessName, String contactName, String email,
                                  String countryCode, String phone, String industry,
                                  String monthlyCallVolume, String channels, String primaryUseCase,
                                  String notes, String status, OffsetDateTime createdAt) {
        static DemoRequestItem from(DemoRequest request) {
            return new DemoRequestItem(request.getId(), request.getBusinessName(), request.getContactName(),
                    request.getEmail(), request.getCountryCode(), request.getPhone(), request.getIndustry(),
                    request.getMonthlyCallVolume(), request.getChannels(), request.getPrimaryUseCase(),
                    request.getNotes(), request.getStatus(), request.getCreatedAt());
        }
    }

    public record DemoRequestPage(List<DemoRequestItem> requests, long total, int page, int pageSize) { }
}
