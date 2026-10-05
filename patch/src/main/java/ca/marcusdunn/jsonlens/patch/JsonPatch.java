package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import java.util.List;

/// A JSON Patch (RFC 6902): a sequence of operations that changes a JSON document.
///
/// [#parse(Object, JsonModel)] reads a patch document with the model of any JSON library. The
/// patch keeps the values of its operations as nodes of that document. [#apply] then changes a
/// target document in place, through a model that is also a [JsonFactory] and a [JsonEditor], for
/// example the Jackson model:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.PatchSnippets region=apply}
///
/// [#applyToCopy] builds a changed copy and does not change the document. It needs only a
/// [JsonModel] and a [JsonFactory], so it also works for immutable values, for example
/// kotlinx.serialization trees.
///
/// | Method | Target model | The document | `copy` |
/// |---|---|---|---|
/// | [#apply] | `JsonModel`, `JsonFactory`, `JsonEditor` | changes in place | one deep copy |
/// | [#applyToCopy] | `JsonModel`, `JsonFactory` | does not change | shares its source |
///
/// ## Atomic application
///
/// The operations apply in order. If an operation fails, the method returns the [PatchError]
/// (RFC 6902, Section 5), and the document has its original value. [#apply] reverses the changes
/// that it made. Because the order of the members of an object is not significant (RFC 8259,
/// Section 4), a reversed member can be at a different position in its object. [#applyToCopy]
/// never changes the document.
///
/// ## Copies
///
/// The patch reads its document and the target document directly. `move` moves a node without a
/// copy, and `test` compares without a copy. `add` and `replace` copy their value once into the
/// target, so that the target never shares a node with the patch. [#apply] makes one deep copy
/// for `copy`, so that two locations do not share a node. [#applyToCopy] builds a new container
/// for each container on a changed path, and shares all other nodes.
///
/// ## Duplicate member names
///
/// An operation must have exactly one `op` and one `path` member (RFC 6902, Section 4). The patch
/// finds duplicates through [JsonModel#hasDuplicate(Object, ca.marcusdunn.jsonlens.model.JsonString)],
/// so read the patch document with a model that keeps duplicate names (the memory-mapped model),
/// or with a parser that rejects them (Jackson with `StreamReadFeature.STRICT_DUPLICATE_DETECTION`).
/// A parser that keeps only one of the values hides the duplicate from the patch.
///
/// @param <P> the node type of the model of the patch document
public final class JsonPatch<P> {

    private final List<Operation<P>> operations;
    private final JsonModel<P> model;

    private JsonPatch(List<Operation<P>> operations, JsonModel<P> model) {
        this.operations = operations;
        this.model = model;
    }

    /// Reads a patch document (RFC 6902, Sections 3 and 4).
    ///
    /// The document must be an array of operation objects. Each operation must have exactly one
    /// `op` and one `path`, and the members that its operation needs. Other members are ignored.
    ///
    /// @param document the patch document
    /// @param model the model of the patch document
    /// @param <P> the node type of the model
    /// @return the patch, or the [PatchError] of the first operation that is not valid
    public static <P> Result<JsonPatch<P>, PatchError> parse(P document, JsonModel<P> model) {
        return new PatchReader<>(model).read(document).map(operations -> new JsonPatch<>(operations, model));
    }

    /// Returns a patch with the given operations.
    ///
    /// @param operations the operations, in order
    /// @param model the model of the values of the operations
    /// @param <P> the node type of the model
    /// @return the patch
    public static <P> JsonPatch<P> of(List<Operation<P>> operations, JsonModel<P> model) {
        return new JsonPatch<>(List.copyOf(operations), model);
    }

    /// Returns the operations.
    ///
    /// @return the operations, in order. The list cannot be changed.
    public List<Operation<P>> operations() {
        return operations;
    }

    /// Returns the model of the values of the operations.
    ///
    /// @return the model of the patch document
    public JsonModel<P> model() {
        return model;
    }

    /// Applies the patch to a document, in place.
    ///
    /// The method changes the containers of the document through the model. An operation with
    /// the whole document as its target (`""`) replaces the root, so use the returned root after
    /// the patch.
    ///
    /// @param document the root of the target document
    /// @param target the model of the target document
    /// @param <N> the node type of the target document
    /// @param <M> the type of the target model
    /// @return the root of the changed document, or the [PatchError] of the first operation that
    ///     failed. After an error, the document has its original value.
    public <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> Result<N, PatchError> apply(N document, M target) {
        return new InPlaceApplier<>(model, target, document).apply(operations);
    }

    /// Applies the patch to a copy of a document. The document does not change.
    ///
    /// The method needs only a [JsonModel] and a [JsonFactory], so it works for immutable values,
    /// for example kotlinx.serialization trees. For each change, it builds a new container for the
    /// changed parent and for each ancestor (path copying). The result shares all other nodes with
    /// the document, so do not change one of them in place while you use the other. `copy` shares
    /// its source node for the same reason. The values of `add` and `replace` are copied once from
    /// the patch document.
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.PatchSnippets region=apply-to-copy}
    ///
    /// @param document the root of the target document. It does not change.
    /// @param target the model of the target document
    /// @param <N> the node type of the target document
    /// @param <M> the type of the target model
    /// @return the root of the changed copy, or the [PatchError] of the first operation that
    ///     failed
    public <N, M extends JsonModel<N> & JsonFactory<N>> Result<N, PatchError> applyToCopy(N document, M target) {
        return new CopyingApplier<>(model, target, document).apply(operations);
    }
}
