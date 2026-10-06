package ca.marcusdunn.jsonlens.benchmarks;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/// The time and the allocation of one evaluation of a query on a loaded document.
///
/// The document is loaded once for each trial, so the load is not in the measurement.
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class QueryBenchmark {

    /// The queries, by name. Each one stresses a different part of the evaluator.
    static final Map<String, String> QUERIES = Map.of(
            // The descendant segment visits each value of the document.
            "descendant", "$..price",
            // A wildcard and name selectors on each record.
            "wildcard", "$.items[*].author.last",
            // A comparison of a number with a literal for each record.
            "filterNumber", "$.items[?@.price < 10]",
            // A comparison of a string with a literal for each record.
            "filterString", "$.items[?@.category == 'poetry']",
            // A regular expression for each record.
            "filterMatch", "$.items[?match(@.author.last, 'L1.*7 .')]",
            // One value: the cost that does not depend on the size.
            "index", "$.items[-1].id");

    @Param({"jackson", "kotlinx", "mapped"})
    public String model;

    @Param({"descendant", "wildcard", "filterNumber", "filterString", "filterMatch", "index"})
    public String query;

    @Param({"100"})
    public int megabytes;

    private Model.Loaded<?> document;
    private JsonPathQuery parsed;
    private final JsonPathEvaluator evaluator = JsonPathEvaluator.standard();

    @Setup(Level.Trial)
    public void setUp() {
        document = Model.of(model).load(BenchmarkData.file(megabytes));
        parsed = switch (JsonPathParser.standard().parse(QUERIES.get(query))) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery value) -> value;
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> throw new IllegalStateException(error.message());
        };
    }

    @Benchmark
    public List<? extends Node<?>> evaluate() {
        return document.evaluate(evaluator, parsed);
    }
}
