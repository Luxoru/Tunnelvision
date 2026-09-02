package dev.luxoru.tunnelvision.server.api;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.detection.Detection;
import dev.luxoru.tunnelvision.detection.Verdict;
import dev.luxoru.tunnelvision.server.api.generated.model.CategoryDto;
import dev.luxoru.tunnelvision.server.api.generated.model.ConfidenceDto;
import dev.luxoru.tunnelvision.server.api.generated.model.DetectionDto;
import dev.luxoru.tunnelvision.server.api.generated.model.SourceStatusDto;
import dev.luxoru.tunnelvision.server.api.generated.model.VerdictDto;
import dev.luxoru.tunnelvision.server.api.generated.model.VerdictStatusDto;
import dev.luxoru.tunnelvision.source.registry.SourceStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ApiModels {
    private ApiModels() {}

    public static VerdictDto verdict(Verdict verdict) {
        Set<CategoryDto> categories = new LinkedHashSet<>();
        verdict.categories().forEach(category -> categories.add(category(category)));

        final var verdictStatus = VerdictStatusDto.fromValue(verdict.status().name());
        final var detections = verdict.detections().stream().map(ApiModels::detection).toList();

        return new VerdictDto(verdict.ip(),
                verdict.risk(),
                verdict.blocked(),
                verdictStatus,
                categories,
                detections
            )
            .provider(verdict.provider());
    }

    public static DetectionDto detection(Detection detection) {
        return new DetectionDto(detection.source(),
                detection.name(),
                category(detection.category()),
                confidence(detection.confidence()),
                detection.match());
    }

    public static SourceStatusDto sourceStatus(SourceStatus status) {

        final var state = SourceStatusDto.StateEnum.fromValue(status.state().name());

        final var offsetDateTime = status.refreshedAt() == null
                ? null
                : status.refreshedAt().atOffset(ZoneOffset.UTC);

        return new SourceStatusDto(status.id(),
                status.name(),
                category(status.category()),
                confidence(status.confidence()),
                state,
                status.entries(), status.blocks())
            .refreshedAt(offsetDateTime)
            .lastError(status.lastError());
    }

    public static CategoryDto category(Category category) {
        return CategoryDto.fromValue(category.name());
    }

    public static ConfidenceDto confidence(Confidence confidence) {
        return ConfidenceDto.fromValue(confidence.name());
    }
}
