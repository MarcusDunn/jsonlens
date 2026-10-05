package ca.marcusdunn.jsonlens.benchmarks;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Measures the heap that stays in use: after a model loads a document, and after a full walk of
/// the document (`$..price`). JMH measures time and allocation, but not this.
///
/// Usage: `Footprint <megabytes> <output file> [models]`. `models` is a comma-separated list, for
/// example `mapped`; the default is all models. The heap values are approximate: they come from
/// the heap use after a full garbage collection.
public final class Footprint {

    private Footprint() {}

    public static void main(String[] args) throws IOException {
        int megabytes = Integer.parseInt(args[0]);
        Path file = BenchmarkData.file(megabytes);
        JsonPathQuery walk = switch (JsonPathParser.standard().parse("$..price")) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery value) -> value;
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> throw new IllegalStateException(error.message());
        };
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%-8s %10s %16s %16s", "model", "file MB", "after load MB", "after walk MB"));
        long empty = usedHeap();
        List<Model> models = args.length > 2
                ? java.util.Arrays.stream(args[2].split(",")).map(Model::of).toList()
                : List.of(Model.values());
        for (Model model : models) {
            Model.Loaded<?> document = model.load(file);
            long loaded = usedHeap() - empty;
            int count = document.evaluate(JsonPathEvaluator.standard(), walk).size();
            long walked = usedHeap() - empty;
            lines.add(String.format("%-8s %10d %16.1f %16.1f", model.name().toLowerCase(java.util.Locale.ROOT),
                    megabytes, loaded / 1048576.0, walked / 1048576.0));
            if (count == 0 || document.root() == null) {
                throw new IllegalStateException("the walk selected no nodes");
            }
        }
        lines.forEach(System.out::println);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, lines);
    }

    /// The heap in use after full garbage collections, in bytes.
    private static long usedHeap() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
