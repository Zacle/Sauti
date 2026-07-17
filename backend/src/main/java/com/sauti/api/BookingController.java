package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.calendar.BookingDtos.BookingResponse;
import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.calendar.BookingDtos.RescheduleBookingRequest;
import com.sauti.calendar.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    List<BookingResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return bookingService.list(user.tenantId()).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    BookingResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return BookingResponse.from(bookingService.get(user.tenantId(), id));
    }

    @PostMapping
    BookingResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateBookingRequest request) {
        return BookingResponse.from(bookingService.create(user.tenantId(), request));
    }

    @DeleteMapping("/{id}")
    BookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return BookingResponse.from(bookingService.cancel(user.tenantId(), id));
    }

    @PatchMapping("/{id}")
    BookingResponse reschedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                               @Valid @RequestBody RescheduleBookingRequest request) {
        return BookingResponse.from(bookingService.reschedule(user.tenantId(), id, request));
    }
}
