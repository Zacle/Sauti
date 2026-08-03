package com.sauti.admin;

import com.sauti.admin.AdminDtos.DemoRequestItem;
import com.sauti.admin.AdminDtos.DemoRequestPage;
import com.sauti.admin.AdminDtos.Overview;
import com.sauti.calendar.BookingRepository;
import com.sauti.call.CallRepository;
import com.sauti.demo.DemoRequestRepository;
import com.sauti.demo.PilotInvitationDtos.InvitationIssued;
import com.sauti.demo.PilotInvitationService;
import com.sauti.tenant.TenantRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final TenantRepository tenants;
    private final CallRepository calls;
    private final BookingRepository bookings;
    private final DemoRequestRepository demoRequests;
    private final PilotInvitationService invitations;

    public AdminService(TenantRepository tenants, CallRepository calls, BookingRepository bookings,
                        DemoRequestRepository demoRequests, PilotInvitationService invitations) {
        this.tenants = tenants;
        this.calls = calls;
        this.bookings = bookings;
        this.demoRequests = demoRequests;
        this.invitations = invitations;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(tenants.count(), calls.count(), bookings.count(), calls.countDistinctCustomerNumbers(),
                demoRequests.countByStatus("new"), demoRequests.countByStatus("invited"),
                demoRequests.countByStatus("activated"));
    }

    @Transactional(readOnly = true)
    public DemoRequestPage demoRequests(int requestedPage, int requestedPageSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.min(100, Math.max(10, requestedPageSize));
        var result = demoRequests.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return new DemoRequestPage(result.getContent().stream().map(DemoRequestItem::from).toList(),
                result.getTotalElements(), page, size);
    }

    public InvitationIssued invite(UUID requestId) {
        return invitations.issue(requestId);
    }
}
