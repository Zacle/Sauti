package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ManagedVoiceAgentProvisioningService {
    private final ManagedVoiceAgentBindingRepository repository;
    private final ManagedVoiceAgentBlueprintFactory blueprintFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, ManagedVoiceAgentProvisioner> provisioners;
    private final Map<String, Object> synchronizationLocks = new ConcurrentHashMap<>();

    public ManagedVoiceAgentProvisioningService(
            ManagedVoiceAgentBindingRepository repository,
            ManagedVoiceAgentBlueprintFactory blueprintFactory,
            ObjectMapper objectMapper,
            List<ManagedVoiceAgentProvisioner> provisioners
    ) {
        this.repository = repository;
        this.blueprintFactory = blueprintFactory;
        this.objectMapper = objectMapper;
        this.provisioners = provisioners.stream().collect(Collectors.toUnmodifiableMap(
                provisioner -> normalize(provisioner.provider()),
                Function.identity()
        ));
    }

    public boolean isConfigured(String provider) {
        var provisioner = provisioners.get(normalize(provider));
        return provisioner != null && provisioner.isConfigured();
    }

    public ManagedVoiceAgentReference resolve(String provider, Call call, String greeting) {
        var normalizedProvider = normalize(provider);
        var provisioner = provisioners.get(normalizedProvider);
        if (provisioner == null) {
            throw new IllegalArgumentException("Unsupported managed voice provider: " + provider);
        }
        if (!provisioner.isConfigured()) {
            throw new VoiceRuntimeUnavailableException(
                    normalizedProvider + " test calls require its provider API key in the running backend."
            );
        }
        var lockKey = call.getAgent().getId() + ":" + normalizedProvider;
        var lock = synchronizationLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                var blueprint = blueprintFactory.create(call, greeting);
                var blueprintHash = hash(blueprint, provisioner.configurationVersion());
                var existingBinding = repository.findByTenantIdAndAgentIdAndProvider(
                        call.getTenant().getId(),
                        call.getAgent().getId(),
                        normalizedProvider
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
                            normalizedProvider,
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

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
