package ca.marcusdunn.jsonlens.benchmarks;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Compares two JMH result files: the time and the allocation of each benchmark.
///
/// Usage: `Compare <base.json> <new.json>`. A negative change is an improvement.
public final class Compare {

    private Compare() {}

    /// The score and the allocation of one benchmark.
    private record Score(double time, String unit, double bytes) {}

    public static void main(String[] args) {
        Map<String, Score> base = read(new File(args[0]));
        Map<String, Score> next = read(new File(args[1]));
        Map<String, Score> keys = new TreeMap<>(base);
        keys.putAll(next);
        System.out.printf("%-58s %12s %12s %8s %14s %14s %8s%n",
                "benchmark", "base", "new", "change", "base B/op", "new B/op", "change");
        for (String key : keys.keySet()) {
            Score a = base.get(key);
            Score b = next.get(key);
            String unit = (b != null ? b : a).unit();
            System.out.printf("%-58s %12s %12s %8s %14s %14s %8s%n",
                    key,
                    a == null ? "-" : String.format("%.3f", a.time()),
                    b == null ? "-" : String.format("%.3f %s", b.time(), unit),
                    a == null || b == null ? "" : change(a.time(), b.time()),
                    a == null ? "-" : String.format("%,.0f", a.bytes()),
                    b == null ? "-" : String.format("%,.0f", b.bytes()),
                    a == null || b == null ? "" : change(a.bytes(), b.bytes()));
        }
    }

    private static String change(double before, double after) {
        return before == 0 ? "" : String.format("%+.1f%%", 100 * (after - before) / before);
    }

    private static Map<String, Score> read(File file) {
        Map<String, Score> scores = new LinkedHashMap<>();
        if (!file.exists()) {
            System.err.println("No result file: " + file);
            return scores;
        }
        for (JsonNode run : JsonMapper.builder().build().readTree(file)) {
            String name = run.get("benchmark").asString();
            StringBuilder key = new StringBuilder(name.substring(name.lastIndexOf('.', name.lastIndexOf('.') - 1) + 1));
            run.path("params").properties().forEach(e -> key.append(' ').append(e.getKey()).append('=').append(e.getValue().asString()));
            JsonNode primary = run.get("primaryMetric");
            JsonNode allocation = run.path("secondaryMetrics").path("gc.alloc.rate.norm");
            scores.put(key.toString(), new Score(
                    primary.get("score").asDouble(),
                    primary.get("scoreUnit").asString(),
                    allocation.isMissingNode() ? 0 : allocation.get("score").asDouble()));
        }
        return scores;
    }
}
