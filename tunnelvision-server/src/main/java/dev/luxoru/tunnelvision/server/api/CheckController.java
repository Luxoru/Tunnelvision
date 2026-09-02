package dev.luxoru.tunnelvision.server.api;

import dev.luxoru.tunnelvision.TunnelVision;
import dev.luxoru.tunnelvision.detection.Verdict;
import dev.luxoru.tunnelvision.server.api.generated.ChecksApi;
import dev.luxoru.tunnelvision.server.api.generated.model.BatchCheckRequestDto;
import dev.luxoru.tunnelvision.server.api.generated.model.BatchCheckResponseDto;
import dev.luxoru.tunnelvision.server.api.generated.model.VerdictDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CheckController implements ChecksApi {
    private final TunnelVision tunnelVision;
    private final Counter blocked, allowed;

    public CheckController(TunnelVision tunnelVision, MeterRegistry meters) {
        this.tunnelVision = tunnelVision;
        this.blocked = Counter.builder("tunnelvision.checks").tag("result", "blocked").register(meters);
        this.allowed = Counter.builder("tunnelvision.checks").tag("result", "allowed").register(meters);
    }

    @Override
    public ResponseEntity<VerdictDto> checkAddress(String ip) {
        return ResponseEntity.ok(check(ip));
    }

    @Override
    public ResponseEntity<BatchCheckResponseDto> checkAddresses(BatchCheckRequestDto request) {
        List<VerdictDto> verdicts = request.getIps().stream().map(this::check).toList();
        return ResponseEntity.ok(new BatchCheckResponseDto(verdicts));
    }

    private VerdictDto check(String ip) {
        Verdict verdict = tunnelVision.check(ip);
        (verdict.blocked() ? blocked : allowed).increment();
        return ApiModels.verdict(verdict);
    }
}
