package ca.marcusdunn.jsonlens.benchmarks;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxNode;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxObjectModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JvmStreamsKt;
import tools.jackson.databind.json.JsonMapper;

/// The models of the benchmarks, and how each one reads a file.
enum Model {
    /// Jackson 3 with the default mapper: a number with a fraction is a `double`.
    JACKSON {
        @Override
        Loaded<?> load(Path file) {
            return Loaded.of(JsonMapper.builder().build().readTree(file.toFile()), JacksonJsonModel.INSTANCE);
        }
    },
    /// kotlinx.serialization, read from a stream, so the text is never one large `String`.
    KOTLINX {
        @Override
        Loaded<?> load(Path file) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
                JsonElement root = JvmStreamsKt.decodeFromStream(Json.Default, JsonElement.Companion.serializer(), in);
                return Loaded.of(root, KotlinxJsonModel.INSTANCE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    },
    /// Kotlin objects through their serializers ([KotlinxObjectModel]). The load decodes the file
    /// into [Items] once. Each evaluation starts from a new root node, so it includes the capture
    /// of each level that the query visits, as for an object that a program queries one time.
    KOTLINXOBJECTS {
        @Override
        Loaded<?> load(Path file) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
                Items items = JvmStreamsKt.decodeFromStream(Json.Default, Items.Companion.serializer(), in);
                KotlinxObjectModel model = new KotlinxObjectModel(Json.Default);
                return new Loaded<KotlinxNode>(() -> model.node(items, Items.Companion.serializer()), model);
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
                case Result.Ok<MappedJson, MappedJsonError>(MappedJson json) -> Loaded.of(json.root(), json.model());
                case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) ->
                        throw new IllegalStateException(error.message());
            };
        }
    };

    abstract Loaded<?> load(Path file);

    static Model of(String name) {
        return valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }

    /// A loaded document and its model. [#root()] gives the root node of an evaluation: the same
    /// node each time, or a new one for a model that caches what a query visits.
    record Loaded<N>(Supplier<N> roots, JsonModel<N> model) {
        static <N> Loaded<N> of(N root, JsonModel<N> model) {
            return new Loaded<>(() -> root, model);
        }

        N root() {
            return roots.get();
        }

        List<Node<N>> evaluate(JsonPathEvaluator evaluator, JsonPathQuery query) {
            return switch (evaluator.evaluate(query, root(), model)) {
                case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> nodes) -> nodes;
                case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) ->
                        throw new IllegalStateException(error.message());
            };
        }
    }
}
