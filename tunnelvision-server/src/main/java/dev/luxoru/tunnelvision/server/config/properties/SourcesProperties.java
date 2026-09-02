package dev.luxoru.tunnelvision.server.config.properties;

import java.util.List;

public record SourcesProperties(List<String> disabled,
                                List<CustomSourceProperties> custom) {
}
