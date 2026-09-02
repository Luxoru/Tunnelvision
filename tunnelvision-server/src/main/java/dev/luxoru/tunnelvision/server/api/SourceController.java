package dev.luxoru.tunnelvision.server.api;

import dev.luxoru.tunnelvision.TunnelVision;
import dev.luxoru.tunnelvision.server.api.generated.SourcesApi;
import dev.luxoru.tunnelvision.server.api.generated.model.SourceStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SourceController implements SourcesApi {

    private final TunnelVision tunnelVision;

    public SourceController(TunnelVision tunnelVision) {
        this.tunnelVision = tunnelVision;
    }

    @Override
    public ResponseEntity<List<SourceStatusDto>> listSources() {
        return ResponseEntity.ok(tunnelVision.sources().stream().map(ApiModels::sourceStatus).toList());
    }
}
