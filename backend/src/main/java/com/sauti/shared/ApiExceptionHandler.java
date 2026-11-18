package com.sauti.shared;

import jakarta.persistence.EntityNotFoundException;
import com.sauti.auth.RateLimitExceededException;
import com.sauti.auth.RegistrationClosedException;
import com.sauti.auth.UnverifiedEmailException;
import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.WorkspaceRequiredException;
import com.sauti.tenant.domain.InvitationLimitExceededException;
import com.sauti.tenant.domain.InvitationUnavailableException;
import com.sauti.tenant.domain.MembershipRuleException;
import com.sauti.tenant.domain.WorkspaceActionForbiddenException;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import com.sauti.call.PublicWebVoiceRateLimitExceededException;
import com.sauti.call.ManagedVoiceProviderException;
import com.sauti.call.VoiceRuntimeUnavailableException;
import com.sauti.demo.DemoRequestRateLimitExceededException;
import com.sauti.demo.PublicDemoVoiceLimitExceededException;
import com.sauti.demo.PilotInvitationUnavailableException;
import com.sauti.billing.PaidAccessRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PaidAccessRequiredException.class)
    ResponseEntity<ApiError> paidAccessRequired(PaidAccessRequiredException exception) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ApiError("paid_access_required", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("bad_request", exception.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ApiError> notFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("not_found", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("forbidden", "Access denied"));
    }

    @ExceptionHandler(UnverifiedEmailException.class)
    ResponseEntity<ApiError> unverified(UnverifiedEmailException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("email_not_verified", exception.getMessage()));
    }

    @ExceptionHandler(RegistrationClosedException.class)
    ResponseEntity<ApiError> registrationClosed(RegistrationClosedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("registration_closed", exception.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> rateLimited(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiError("rate_limited", exception.getMessage()));
    }

    @ExceptionHandler(PublicWebVoiceRateLimitExceededException.class)
    ResponseEntity<ApiError> webVoiceRateLimited(PublicWebVoiceRateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("rate_limited", exception.getMessage()));
    }

    @ExceptionHandler(DemoRequestRateLimitExceededException.class)
    ResponseEntity<ApiError> demoRequestRateLimited(DemoRequestRateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("rate_limited", exception.getMessage()));
    }

    @ExceptionHandler(PublicDemoVoiceLimitExceededException.class)
    ResponseEntity<ApiError> publicDemoVoiceLimited(PublicDemoVoiceLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("public_demo_limit_reached", exception.getMessage()));
    }

    @ExceptionHandler(PilotInvitationUnavailableException.class)
    ResponseEntity<ApiError> pilotInvitationUnavailable(PilotInvitationUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiError("pilot_invitation_unavailable", exception.getMessage()));
    }

    @ExceptionHandler(VoiceRuntimeUnavailableException.class)
    ResponseEntity<ApiError> voiceRuntimeUnavailable(VoiceRuntimeUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("voice_runtime_unavailable", exception.getMessage()));
    }

    @ExceptionHandler(ManagedVoiceProviderException.class)
    ResponseEntity<ApiError> managedVoiceProvider(ManagedVoiceProviderException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("managed_voice_provider_error", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSessionException.class)
    ResponseEntity<ApiError> invalidSession(InvalidSessionException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("session_expired", exception.getMessage()));
    }

    @ExceptionHandler(WorkspaceRequiredException.class)
    ResponseEntity<ApiError> workspaceRequired(WorkspaceRequiredException exception) {
        return ResponseEntity.badRequest().body(new ApiError("workspace_required", exception.getMessage()));
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    ResponseEntity<ApiError> workspaceNotFound(WorkspaceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("not_found", exception.getMessage()));
    }

    @ExceptionHandler(WorkspaceActionForbiddenException.class)
    ResponseEntity<ApiError> workspaceActionForbidden(WorkspaceActionForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("forbidden", exception.getMessage()));
    }

    @ExceptionHandler(MembershipRuleException.class)
    ResponseEntity<ApiError> membershipRule(MembershipRuleException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("conflict", exception.getMessage()));
    }

    @ExceptionHandler(InvitationUnavailableException.class)
    ResponseEntity<ApiError> invitationUnavailable(InvitationUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(new ApiError("invitation_unavailable", exception.getMessage()));
    }

    @ExceptionHandler(InvitationLimitExceededException.class)
    ResponseEntity<ApiError> invitationLimit(InvitationLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiError("rate_limited", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ApiError("validation_error", message));
    }
}
