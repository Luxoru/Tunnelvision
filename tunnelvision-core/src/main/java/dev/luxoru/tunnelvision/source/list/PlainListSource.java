package dev.luxoru.tunnelvision.source.list;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.Sources;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record PlainListSource(String id, String name, Category category, Confidence confidence,
                              Duration refreshInterval, List<String> urls) implements DetectionSource {

    @Override
    public List<String> fetch(SourceContext context) throws Exception {
        List<String> entries = new ArrayList<>();
        for (String url : urls) {
            entries.addAll(parse(context.get(url)));
        }
        return entries;
    }

    public static List<String> parse(String body) {
        List<String> entries = new ArrayList<>();
        for (String line : body.split("\\R")) {
            int comment = indexOfComment(line);
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.strip();
            if (!line.isEmpty()) {
                entries.add(line.split("[\\s,]+", 2)[0]);
            }
        }
        return entries;
    }

    private static int indexOfComment(String line) {
        int hash = line.indexOf('#'), semicolon = line.indexOf(';');
        if (hash < 0) {
            return semicolon;
        }
        return semicolon < 0 ? hash : Math.min(hash, semicolon);
    }
}
