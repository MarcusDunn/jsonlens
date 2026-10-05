package ca.marcusdunn.jsonlens.benchmarks;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;

/// Makes the JSON files of the benchmarks.
///
/// A file is `{"items": [...]}` with records of about 300 bytes each. The content depends only on
/// the size, so each run uses the same file. The file stays in the data directory (the system
/// property `jsonlens.benchmark.data`) for the next run.
///
/// A record:
///
/// ```json
/// {"id":17,"name":"Item 17 né","category":"poetry","price":12.34,"stock":5,"available":true,
///  "tags":["t3","t9"],"ratings":[4,1,5],"author":{"first":"F17","last":"L17 中"},"note":null}
/// ```
final class BenchmarkData {

    private static final String[] CATEGORIES = {"fiction", "reference", "science", "history", "poetry"};

    private BenchmarkData() {}

    /// Returns the file of a size. It makes the file if it does not exist.
    static Path file(int megabytes) {
        Path directory = Path.of(System.getProperty("jsonlens.benchmark.data", "build/benchmark-data"));
        Path file = directory.resolve("items-" + megabytes + "mb.json");
        if (Files.exists(file)) {
            return file;
        }
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "items-", ".tmp");
            try (Writer out = new BufferedWriter(Files.newBufferedWriter(temporary, StandardCharsets.UTF_8), 1 << 16)) {
                write(out, (long) megabytes * 1024 * 1024);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Writer out, long size) throws IOException {
        Random random = new Random(size);
        StringBuilder record = new StringBuilder(512);
        long written = 0;
        out.write("{\"items\":[");
        for (long id = 0; written < size; id++) {
            record.setLength(0);
            if (id > 0) {
                record.append(',');
            }
            record.append("{\"id\":").append(id)
                    .append(",\"name\":\"Item ").append(id).append(" né\"")
                    .append(",\"category\":\"").append(CATEGORIES[random.nextInt(CATEGORIES.length)]).append('"')
                    .append(",\"price\":").append(random.nextInt(10_000) / 100).append('.')
                    .append(String.format("%02d", random.nextInt(100)))
                    .append(",\"stock\":").append(random.nextInt(1000))
                    .append(",\"available\":").append(random.nextBoolean())
                    .append(",\"tags\":[\"t").append(random.nextInt(10)).append("\",\"t").append(random.nextInt(10)).append("\"]")
                    .append(",\"ratings\":[").append(random.nextInt(5) + 1).append(',').append(random.nextInt(5) + 1)
                    .append(',').append(random.nextInt(5) + 1).append(']')
                    .append(",\"author\":{\"first\":\"F").append(id).append("\",\"last\":\"L").append(id).append(" 中\"}")
                    .append(",\"note\":null}");
            out.append(record);
            written += record.length();
        }
        out.write("]}");
    }
}
