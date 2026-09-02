package dev.luxoru.tunnelvision.server.config.properties;

import dev.luxoru.tunnelvision.detection.Category;

import java.util.Set;

public record PolicyProperties(Set<Category> blockCategories,
                               double riskThreshold) {
}
