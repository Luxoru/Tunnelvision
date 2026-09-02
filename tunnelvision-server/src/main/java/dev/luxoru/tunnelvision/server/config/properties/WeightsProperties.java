package dev.luxoru.tunnelvision.server.config.properties;

import dev.luxoru.tunnelvision.detection.Category;

import java.util.Map;

public record WeightsProperties(Map<Category, Double> confirmed,
                                Map<Category, Double> likely) {
}
