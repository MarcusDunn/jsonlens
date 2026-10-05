package ca.marcusdunn.jsonlens.benchmarks;

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

/// The time and the allocation to load a document: the parse of Jackson and kotlinx, or the map
/// and the structural index of the memory-mapped model.
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(1)
public class LoadBenchmark {

    @Param({"jackson", "kotlinx", "mapped"})
    public String model;

    @Param({"100"})
    public int megabytes;

    private java.nio.file.Path file;

    @Setup(Level.Trial)
    public void setUp() {
        file = BenchmarkData.file(megabytes);
    }

    @Benchmark
    public Model.Loaded<?> load() {
        return Model.of(model).load(file);
    }
}
