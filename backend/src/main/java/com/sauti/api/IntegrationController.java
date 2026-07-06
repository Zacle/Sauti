package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.integration.IntegrationCatalog;
import com.sauti.integration.IntegrationService;
import com.sauti.integration.IntegrationService.BindingRequest;
import com.sauti.integration.IntegrationService.BindingResponse;
import com.sauti.integration.IntegrationService.ConnectionRequest;
import com.sauti.integration.IntegrationService.ConnectionResponse;
import com.sauti.integration.IntegrationService.DeliveryResponse;
import com.sauti.integration.ProviderOAuthService;
import com.sauti.integration.WhatsAppEmbeddedSignupService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1")
public class IntegrationController {
    private final IntegrationCatalog catalog;
    private final IntegrationService service;
    private final ProviderOAuthService oauth;
    private final WhatsAppEmbeddedSignupService whatsappSignup;
    private final String dashboardBaseUrl;

    public IntegrationController(IntegrationCatalog catalog, IntegrationService service,
                                 ProviderOAuthService oauth,
                                 WhatsAppEmbeddedSignupService whatsappSignup,
                                 @Value("${sauti.dashboard.base-url}") String dashboardBaseUrl) {
        this.catalog = catalog;
        this.service = service;
        this.oauth = oauth;
        this.whatsappSignup = whatsappSignup;
        this.dashboardBaseUrl = dashboardBaseUrl;
    }

    @GetMapping("/integrations/catalog")
    List<CatalogEntryResponse> catalog() {
        return catalog.all().stream().map(entry -> new CatalogEntryResponse(
                entry.provider(), entry.name(), entry.category(), entry.description(),
                entry.duringCall(), entry.postCall(), entry.requiresConnection(),
                entry.configurationFields(), entry.credentialFields(),
                !java.util.Set.of("google_sheets", "hubspot", "salesforce").contains(entry.provider())
                        || oauth.configured(entry.provider())
        )).toList();
    }

    @GetMapping("/integrations/connections")
    List<ConnectionResponse> connections(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listConnections(user.tenantId());
    }

    @PostMapping("/integrations/connections")
    @ResponseStatus(HttpStatus.CREATED)
    ConnectionResponse connect(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestBody ConnectionRequest request) {
        return service.create(user.tenantId(), request);
    }

    @PatchMapping("/integrations/connections/{id}")
    ConnectionResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                              @RequestBody ConnectionRequest request) {
        return service.update(user.tenantId(), id, request);
    }

    @DeleteMapping("/integrations/connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.disconnect(user.tenantId(), id);
    }

    @PostMapping("/integrations/connections/{id}/test")
    ConnectionResponse test(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.test(user.tenantId(), id);
    }

    @GetMapping("/agents/{agentId}/integrations")
    List<BindingResponse> bindings(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID agentId) {
        return service.listBindings(user.tenantId(), agentId);
    }

    @PutMapping("/agents/{agentId}/integrations")
    BindingResponse configure(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID agentId,
                              @RequestBody BindingRequest request) {
        return service.configure(user.tenantId(), agentId, request);
    }

    @GetMapping("/integration-deliveries")
    List<DeliveryResponse> deliveries(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listDeliveries(user.tenantId());
    }

    @GetMapping("/integrations/whatsapp/embedded-signup/config")
    WhatsAppEmbeddedSignupService.SignupConfiguration whatsappSignupConfiguration() {
        return whatsappSignup.configuration();
    }

    @PostMapping("/integrations/whatsapp/embedded-signup/complete")
    WhatsAppEmbeddedSignupService.ConnectionResult completeWhatsAppSignup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody WhatsAppEmbeddedSignupService.CompleteRequest request
    ) {
        return whatsappSignup.complete(user.tenantId(), request);
    }

    @GetMapping("/integrations/connections/{id}/whatsapp/templates")
    List<WhatsAppEmbeddedSignupService.TemplateOption> whatsappTemplates(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return whatsappSignup.templates(user.tenantId(), id);
    }

    @GetMapping("/integrations/{provider}/authorize")
    java.util.Map<String, Object> authorize(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable String provider,
                                            @RequestParam UUID agentId) {
        return java.util.Map.of("authorizationUrl", oauth.authorizationUrl(user.tenantId(), agentId, provider));
    }

    @GetMapping("/integrations/{provider}/callback")
    RedirectView callback(@PathVariable String provider,
                          @RequestParam(required = false) String code,
                          @RequestParam(required = false) String state,
                          @RequestParam(required = false) String error) {
        if (error != null || code == null || state == null) {
            return new RedirectView(dashboardBaseUrl + "/dashboard/integrations?oauth=cancelled");
        }
        var agentId = oauth.complete(provider, code, state);
        return new RedirectView(dashboardBaseUrl + "/dashboard/integrations?agentId=" + agentId + "&oauth=connected");
    }

    record CatalogEntryResponse(
            String provider,
            String name,
            String category,
            String description,
            boolean duringCall,
            boolean postCall,
            boolean requiresConnection,
            List<String> configurationFields,
            List<String> credentialFields,
            boolean authorizationConfigured
    ) {}
}
