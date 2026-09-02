package dev.luxoru.tunnelvision.server.health;

import dev.luxoru.tunnelvision.TunnelVision;
import dev.luxoru.tunnelvision.source.registry.SourceStatus;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Out of service until every source has loaded or tried to, so Kubernetes
 * doesn't route traffic to an instance that would answer from no data.
 */
@Component("sources")
public class SourcesHealthIndicator implements HealthIndicator {
    private final TunnelVision tunnelVision;

    public SourcesHealthIndicator(TunnelVision tunnelVision) {
        this.tunnelVision = tunnelVision;
    }

    @Override
    public Health health() {
        Map<String, Object> states = new LinkedHashMap<>();
        for (SourceStatus source : tunnelVision.sources()) {
            states.put(source.id(), source.state());
        }
        return (tunnelVision.isReady() ? Health.up() : Health.outOfService()).withDetails(states).build();
    }
}
