package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ManagedVoiceAgentProvisioningService {
    private final ManagedVoiceAgentBindingRepository repository;
    private final ManagedVoiceAgentBlueprintFactory blueprintFactory;
    private final ObjectMapper objectMapper;
    private final TelnyxManagedVoiceAgentProvisioner provisioner;
    private final Map<String, Object> synchronizationLocks = new ConcurrentHashMap<>();

    public ManagedVoiceAgentProvisioningService(
            ManagedVoiceAgentBindingRepository repository,
            ManagedVoiceAgentBlueprintFactory blueprintFactory,
            ObjectMapper objectMapper,
            TelnyxManagedVoiceAgentProvisioner provisioner
    ) {
        this.repository = repository;
        this.blueprintFactory = blueprintFactory;
        this.objectMapper = objectMapper;
        this.provisioner = provisioner;
    }

    public boolean isConfigured() {
        return provisioner.isConfigured();
    }

    public ManagedVoiceAgentReference resolve(Call call, String greeting) {
        return synchronize(
                call.getTenant(),
                call.getAgent(),
                blueprintFactory.create(call, greeting)
        );
    }

    public ManagedVoiceAgentReference synchronize(com.sauti.agent.Agent agent, String greeting) {
        return synchronize(agent, greeting, agent.getDefaultLanguage());
    }

    public ManagedVoiceAgentReference synchronize(
            com.sauti.agent.Agent agent,
            String greeting,
            String language
    ) {
        var normalizedLanguage = supportedLanguage(agent, language);
        return synchronize(
                agent.getTenant(),
                agent,
                blueprintFactory.create(agent, greeting, normalizedLanguage)
        );
    }

    public List<ManagedVoiceAgentReference> synchronizeAll(
            com.sauti.agent.Agent agent,
            java.util.function.Function<String, String> greeting
    ) {
        return agent.getSupportedLanguages().stream()
                .map(language -> synchronize(agent, greeting.apply(language), language))
                .toList();
    }

    public ManagedVoiceAgentReference existing(Call call) {
        return existing(
                call.getTenant().getId(),
                call.getAgent().getId(),
                selectedLanguage(call)
        );
    }

    private ManagedVoiceAgentReference existing(
            java.util.UUID tenantId,
            java.util.UUID agentId,
            String language
    ) {
        if (!provisioner.isConfigured()) {
            throw new VoiceRuntimeUnavailableException(
                    "Telnyx calls require TELNYX_API_KEY, PUBLIC_BASE_URL, and TELNYX_TOOL_WEBHOOK_SECRET "
                            + "in the running backend."
            );
        }
        return repository.findByTenantIdAndAgentIdAndProviderAndLanguage(
                        tenantId, agentId, "telnyx", language
                )
                .map(this::reference)
                .orElseThrow(() -> new VoiceRuntimeUnavailableException(
                        "This agent is still preparing the " + language
                                + " voice variant. Wait a moment and try again."
                ));
    }

    private ManagedVoiceAgentReference synchronize(
            com.sauti.tenant.Tenant tenant,
            com.sauti.agent.Agent agent,
            ManagedVoiceAgentBlueprint blueprint
    ) {
        if (!provisioner.isConfigured()) {
            throw new VoiceRuntimeUnavailableException(
                    "Telnyx calls require TELNYX_API_KEY, PUBLIC_BASE_URL, and TELNYX_TOOL_WEBHOOK_SECRET "
                            + "in the running backend."
            );
        }
        var language = blueprint.language();
        var lockKey = agent.getId() + ":telnyx:" + language;
        var lock = synchronizationLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                var blueprintHash = hash(blueprint, provisioner.configurationVersion());
                var existingBinding = repository.findByTenantIdAndAgentIdAndProviderAndLanguage(
                        tenant.getId(),
                        agent.getId(),
                        "telnyx",
                        language
                ).orElse(null);
                if (existingBinding != null && blueprintHash.equals(existingBinding.getBlueprintHash())) {
                    return reference(existingBinding);
                }
                var synchronizedReference = provisioner.synchronize(
                        blueprint,
                        existingBinding == null ? null : reference(existingBinding)
                );
                if (existingBinding == null) {
                    existingBinding = new ManagedVoiceAgentBinding(
                            tenant,
                            agent,
                            "telnyx",
                            language,
                            blueprintHash,
                            synchronizedReference
                    );
                } else {
                    existingBinding.synchronize(blueprintHash, synchronizedReference);
                }
                repository.save(existingBinding);
                return synchronizedReference;
            } finally {
                synchronizationLocks.remove(lockKey, lock);
            }
        }
    }

    private String selectedLanguage(Call call) {
        var selected = call.getLanguageDetected();
        return supportedLanguage(call.getAgent(), selected);
    }

    private String supportedLanguage(com.sauti.agent.Agent agent, String language) {
        var normalized = language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) normalized = agent.getDefaultLanguage();
        if (!agent.getSupportedLanguages().contains(normalized)) {
            throw new IllegalArgumentException(
                    "Language " + normalized + " is not supported by this agent"
            );
        }
        return normalized;
    }

    private ManagedVoiceAgentReference reference(ManagedVoiceAgentBinding binding) {
        return new ManagedVoiceAgentReference(
                binding.getExternalAgentId(),
                binding.getExternalVersionId(),
                binding.getExternalResourcesJson()
        );
    }

    private String hash(ManagedVoiceAgentBlueprint blueprint, String configurationVersion) {
        try {
            var writer = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            var fingerprint = Map.of(
                    "blueprint", blueprint,
                    "configurationVersion", configurationVersion == null ? "" : configurationVersion
            );
            var bytes = writer.writeValueAsString(fingerprint).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint the managed voice agent blueprint", exception);
        }
    }
}
