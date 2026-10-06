package ca.marcusdunn.jsonlens.benchmarks;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JvmStreamsKt;
import tools.jackson.databind.json.JsonMapper;

/// The three models of the benchmarks, and how each one reads a file.
enum Model {
    /// Jackson 3 with the default mapper: a number with a fraction is a `double`.
    JACKSON {
        @Override
        Loaded<?> load(Path file) {
            return new Loaded<>(JsonMapper.builder().build().readTree(file.toFile()), JacksonJsonModel.INSTANCE);
        }
    },
    /// kotlinx.serialization, read from a stream, so the text is never one large `String`.
    KOTLINX {
        @Override
        Loaded<?> load(Path file) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
                JsonElement root = JvmStreamsKt.decodeFromStream(Json.Default, JsonElement.Companion.serializer(), in);
                return new Loaded<>(root, KotlinxJsonModel.INSTANCE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    },
    /// The memory-mapped model: the load maps the file and builds the structural index.
    MAPPED {
        @Override
        Loaded<?> load(Path file) {
            return switch (MappedJson.open(file)) {
                case Result.Ok<MappedJson, MappedJsonError>(MappedJson json) -> new Loaded<>(json.root(), json.model());
                case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) ->
                        throw new IllegalStateException(error.message());
            };
        }
    };

    abstract Loaded<?> load(Path file);

    static Model of(String name) {
        return valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }

    /// A root, and the nodes that a query selected from it.
    record Held(Object root, List<?> nodes) {}

    /// A loaded document and its model.
    record Loaded<N>(N root, JsonModel<N> model) {
        List<Node<N>> evaluate(JsonPathEvaluator evaluator, JsonPathQuery query) {
            return evaluate(evaluator, query, root);
        }

        /// Evaluates a query, and keeps its root, so that the caller can measure what the query
        /// left reachable from the root, for example the cache of a model.
        Held hold(JsonPathEvaluator evaluator, JsonPathQuery query) {
            return new Held(root, evaluate(evaluator, query, root));
        }

        private List<Node<N>> evaluate(JsonPathEvaluator evaluator, JsonPathQuery query, N root) {
            return switch (evaluator.evaluate(query, root, model)) {
                case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> nodes) -> nodes;
                case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) ->
                        throw new IllegalStateException(error.message());
            };
        }
    }
}
