# Benchmarks

This project has [JMH](https://github.com/openjdk/jmh) benchmarks with large JSON files, for the
three models: Jackson, kotlinx.serialization, and the memory-mapped model. Use them to measure a
change: run the benchmarks before and after the change, and compare the results.

## Data

`BenchmarkData` makes the file `build/benchmark-data/items-<size>mb.json` the first time a run
needs it. The file is `{"items": [...]}` with records of about 300 bytes: strings with non-ASCII
characters, integers, decimals, booleans, `null`, arrays, and a nested object. The content depends
only on the size, so all runs use the same data.

## Benchmarks

| Benchmark | Measures |
|---|---|
| `QueryBenchmark` | The time (µs/op) and the allocation (B/op) of one evaluation, for each model and query. The load is not in the measurement. |
| `LoadBenchmark` | The time and the allocation to load a file: the parse of Jackson and kotlinx, or the map and the index of the memory-mapped model. |
| `footprint` task | The heap that stays in use after the load, and after a full walk (`$..price`). JMH does not measure this. |

The queries of `QueryBenchmark`:

| Name | Query | Stresses |
|---|---|---|
| `descendant` | `$..price` | the walk of each value |
| `wildcard` | `$.items[*].author.last` | wildcards and name selectors |
| `filterNumber` | `$.items[?@.price < 10]` | number comparisons |
| `filterString` | `$.items[?@.category == 'poetry']` | string comparisons |
| `filterMatch` | `$.items[?match(@.author.last, 'L1.*7 .')]` | regular expressions |
| `index` | `$.items[-1].id` | the cost that does not depend on the size |

## Run

```sh
# All benchmarks with a 100 MB file (about 10 minutes). The result goes to results/<label>.json.
./gradlew :benchmarks:jmh -Plabel=baseline

# Only some benchmarks or parameters. JMH options go in -Pjmh.args.
./gradlew :benchmarks:jmh -Plabel=try -Pjmh.include=QueryBenchmark \
    -Pjmh.params="model=mapped;query=descendant,filterNumber;megabytes=1000" -Pjmh.args="-wi 1 -i 3"

# The heap after a load and after a walk. The result goes to results/<label>-footprint.txt.
./gradlew :benchmarks:footprint -Plabel=baseline -Pmegabytes=100
./gradlew :benchmarks:footprint -Plabel=baseline-1gb -Pmegabytes=1000 -Pmodels=mapped

# Compare two results: time and allocation, with the change in percent.
./gradlew :benchmarks:jmhCompare -Pbase=baseline -Plabel=try
```

The forked JVMs get `-Xmx12g`. Change it with `-Pjmh.heap=16g`. The Jackson and kotlinx trees of a
1 GB file need much heap; use `-Pmodels=mapped` or `model=mapped` if the machine has too little
memory.

## Results

Keep the results of each step in `results/`, with a label that names the change, so that the
history shows each improvement. The numbers depend on the machine: compare only results from the
same machine.
