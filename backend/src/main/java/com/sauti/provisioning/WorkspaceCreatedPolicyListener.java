package com.sauti.provisioning;

import com.sauti.tenant.application.WorkspaceCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Every new workspace starts with a zero-budget, deny-by-default provider policy. A workspace
 * without a policy is unrestricted, so this runs in the creating transaction, not after it.
 */
@Component
class WorkspaceCreatedPolicyListener {
    private final PilotProvisioningPolicyService policies;

    WorkspaceCreatedPolicyListener(PilotProvisioningPolicyService policies) {
        this.policies = policies;
    }

    @EventListener
    void onWorkspaceCreated(WorkspaceCreated event) {
        policies.ensurePending(event.workspaceId());
    }
}
