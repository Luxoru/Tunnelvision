package dev.luxoru.tunnelvision.server.api;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.detection.Detection;
import dev.luxoru.tunnelvision.detection.Verdict;
import dev.luxoru.tunnelvision.server.api.generated.model.CategoryDto;
import dev.luxoru.tunnelvision.server.api.generated.model.SourceStatusDto;
import dev.luxoru.tunnelvision.server.api.generated.model.VerdictDto;
import dev.luxoru.tunnelvision.server.api.generated.model.VerdictStatusDto;
import dev.luxoru.tunnelvision.source.registry.SourceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiModelsTest {
    // These fail when a value is added to core but not to openapi.yaml

    @ParameterizedTest
    @EnumSource(Category.class)
    void everyCategoryIsInTheSpec(Category category) {
        assertThat(ApiModels.category(category).getValue()).isEqualTo(category.name());
    }

    @ParameterizedTest
    @EnumSource(Confidence.class)
    void everyConfidenceIsInTheSpec(Confidence confidence) {
        assertThat(ApiModels.confidence(confidence).getValue()).isEqualTo(confidence.name());
    }

    @ParameterizedTest
    @EnumSource(Verdict.Status.class)
    void everyVerdictStatusIsInTheSpec(Verdict.Status status) {
        Verdict verdict = new Verdict("1.2.3.4", 0, false, status, Set.of(), null, List.of());
        assertThat(ApiModels.verdict(verdict).getStatus().getValue()).isEqualTo(status.name());
    }

    @ParameterizedTest
    @EnumSource(SourceStatus.State.class)
    void everySourceStateIsInTheSpec(SourceStatus.State state) {
        SourceStatus status = new SourceStatus("id", "Name", Category.VPN, Confidence.LIKELY, state, 0, 0, null, null);
        assertThat(ApiModels.sourceStatus(status).getState().getValue()).isEqualTo(state.name());
    }

    @Test
    void mapsAVerdict() {
        Detection detection = new Detection("nordvpn", "NordVPN", Category.VPN, Confidence.CONFIRMED, "45.1.2.0/24");
        Verdict verdict = new Verdict("45.1.2.3", 1, true, Verdict.Status.CHECKED, Set.of(Category.VPN, Category.HOSTING),
            "NordVPN", List.of(detection));

        VerdictDto dto = ApiModels.verdict(verdict);
        assertThat(dto.getIp()).isEqualTo("45.1.2.3");
        assertThat(dto.getRisk()).isEqualTo(1.0);
        assertThat(dto.getBlocked()).isTrue();
        assertThat(dto.getStatus()).isEqualTo(VerdictStatusDto.CHECKED);
        assertThat(dto.getCategories()).containsExactlyInAnyOrder(CategoryDto.VPN, CategoryDto.HOSTING);
        assertThat(dto.getProvider()).isEqualTo("NordVPN");
        assertThat(dto.getDetections()).singleElement().satisfies(match -> {
            assertThat(match.getSource()).isEqualTo("nordvpn");
            assertThat(match.getMatch()).isEqualTo("45.1.2.0/24");
        });
    }

    @Test
    void mapsASourceStatus() {
        Instant refreshedAt = Instant.parse("2026-10-15T12:00:00Z");
        SourceStatus status = new SourceStatus("x4b-vpn", "X4BNet VPN list", Category.VPN, Confidence.LIKELY,
            SourceStatus.State.ACTIVE, 11_000, 9_000, refreshedAt, null);

        SourceStatusDto dto = ApiModels.sourceStatus(status);
        assertThat(dto.getId()).isEqualTo("x4b-vpn");
        assertThat(dto.getState()).isEqualTo(SourceStatusDto.StateEnum.ACTIVE);
        assertThat(dto.getEntries()).isEqualTo(11_000);
        assertThat(dto.getBlocks()).isEqualTo(9_000);
        assertThat(dto.getRefreshedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getLastError()).isNull();
    }

    @Test
    void aSourceThatNeverLoadedHasNoRefreshTime() {
        SourceStatus status = new SourceStatus("tor", "Tor", Category.TOR, Confidence.CONFIRMED,
            SourceStatus.State.FAILED, 0, 0, null, "HttpTimeoutException: timed out");
        SourceStatusDto dto = ApiModels.sourceStatus(status);
        assertThat(dto.getRefreshedAt()).isNull();
        assertThat(dto.getLastError()).isEqualTo("HttpTimeoutException: timed out");
    }
}
