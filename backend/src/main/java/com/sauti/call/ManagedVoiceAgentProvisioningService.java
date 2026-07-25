package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        if (!provisioner.isConfigured()) {
            throw new VoiceRuntimeUnavailableException(
                    "Telnyx calls require TELNYX_API_KEY, PUBLIC_BASE_URL, and TELNYX_TOOL_WEBHOOK_SECRET "
                            + "in the running backend."
            );
        }
        var lockKey = call.getAgent().getId() + ":telnyx";
        var lock = synchronizationLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                var blueprint = blueprintFactory.create(call, greeting);
                var blueprintHash = hash(blueprint, provisioner.configurationVersion());
                var existingBinding = repository.findByTenantIdAndAgentIdAndProvider(
                        call.getTenant().getId(),
                        call.getAgent().getId(),
                        "telnyx"
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
                            call.getTenant(),
                            call.getAgent(),
                            "telnyx",
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
