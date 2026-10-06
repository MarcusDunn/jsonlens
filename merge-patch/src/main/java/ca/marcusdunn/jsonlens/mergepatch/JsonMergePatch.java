package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;

/// A JSON Merge Patch (RFC 7396): a JSON document that describes changes to a target document.
///
/// [#parse(Object, JsonModel)] reads a patch document with the model of any JSON library. The
/// patch keeps the document and reads it again for each merge. [#apply] merges the patch in place
/// into a target, through a model that is also a [JsonFactory] and a [JsonEditor], for example
/// the Jackson model:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.MergePatchSnippets region=apply}
///
/// [#applyToCopy] builds a changed copy and does not change the target. It needs only a
/// [JsonModel] and a [JsonFactory], so it also works for immutable values, for example
/// kotlinx.serialization trees.
///
/// | Method | Target model | The target | Unchanged nodes |
/// |---|---|---|---|
/// | [#apply] | `JsonModel`, `JsonFactory`, `JsonEditor` | changes in place | stay in place |
/// | [#applyToCopy] | `JsonModel`, `JsonFactory` | does not change | shared with the result |
///
/// ## Rules (RFC 7396, Section 2)
///
/// - A patch that is not an object, for example an array or `null`, replaces the whole target.
/// - An object patch merges into the target. If the target is not an object, the merge starts from
///   an empty object.
/// - A member with the value `null` removes the member from the target. If the target has no such
///   member, nothing changes.
/// - A member with an object value merges into the target member, by the same rules. Thus the
///   `null` members of the object never get into the result.
/// - A member with another value adds or replaces the target member. An array replaces the
///   target member as a whole, with all its elements, also `null` elements.
///
/// A merge patch cannot set a member to `null`, and cannot change a part of an array. Use JSON
/// Patch (RFC 6902) for these changes.
///
/// ## Copies
///
/// The merge copies each value of the patch that gets into the result one time into the target
/// model, with [JsonFactory#copyOf(JsonModel, Object)]. Thus the result never shares a node with
/// the patch document. [#apply] builds no other node: it removes and puts members of the objects
/// of the target. [#applyToCopy] also builds a new object for each object of the target that
/// changes, and for each of its ancestors. The result shares all other nodes with the target.
///
/// ## Errors
///
/// The merge reads the patch and the target without recursion, so a deep document cannot overflow
/// the stack. An error is a [MergePatchError] value. The merge finds all changes before it makes
/// one, so after an error the target has its original value.
///
/// ## Duplicate member names
///
/// RFC 7396 does not tell which value of a duplicate member name applies, and RFC 8259 (Section 4)
/// does not define their meaning. [#parse] rejects a patch document with a duplicate name in any
/// object. Read the patch document with a model that keeps duplicate names (the memory-mapped
/// model), or with a parser that rejects them (Jackson with
/// `StreamReadFeature.STRICT_DUPLICATE_DETECTION`). A parser that keeps only one of the values
/// hides the duplicate from the patch.
///
/// @param <P> the node type of the model of the patch document
public final class JsonMergePatch<P> {

    private final P document;
    private final JsonModel<P> model;

    private JsonMergePatch(P document, JsonModel<P> model) {
        this.document = document;
        this.model = model;
    }

    /// Reads a merge patch document (RFC 7396, Section 2).
    ///
    /// Each JSON value is a merge patch. The method reads the whole document one time, to find
    /// duplicate member names. It does not copy the document.
    ///
    /// @param document the patch document
    /// @param model the model of the patch document
    /// @param <P> the node type of the model
    /// @return the patch, or [MergePatchError.DuplicateName] if an object of the document has a
    ///     member name more than once
    public static <P> Result<JsonMergePatch<P>, MergePatchError> parse(P document, JsonModel<P> model) {
        return switch (new DuplicateFinder<>(model).find(document)) {
            case Maybe.Some<MergePatchError>(MergePatchError error) -> Result.err(error);
            case Maybe.None<MergePatchError>() -> Result.ok(new JsonMergePatch<>(document, model));
        };
    }

    /// Returns the patch document.
    ///
    /// @return the root of the patch document
    public P document() {
        return document;
    }

    /// Returns the model of the patch document.
    ///
    /// @return the model of the patch document
    public JsonModel<P> model() {
        return model;
    }

    /// Merges the patch into a target, in place.
    ///
    /// The method changes the objects of the target through the model. A patch that is not an
    /// object, or an object patch for a target that is not an object, replaces the root. Thus use
    /// the returned root after the merge. A replaced root does not change.
    ///
    /// @param target the root of the target document
    /// @param targetModel the model of the target document
    /// @param <N> the node type of the target document
    /// @param <M> the type of the target model
    /// @return the root of the changed target, or the first [MergePatchError]. After an error, the
    ///     target has its original value.
    public <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> Result<N, MergePatchError> apply(
            N target, M targetModel) {
        return new InPlaceMerger<>(model, targetModel).merge(target, document);
    }

    /// Merges the patch into a copy of a target. The target does not change.
    ///
    /// The method needs only a [JsonModel] and a [JsonFactory], so it works for immutable values,
    /// for example kotlinx.serialization trees. It builds a new object for each object of the
    /// target that changes, and for each of its ancestors (path copying). The result shares all
    /// other nodes with the target, so do not change one of them in place while you use the other.
    /// If an object patch changes nothing, the result is the target itself.
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.MergePatchSnippets region=apply-to-copy}
    ///
    /// @param target the root of the target document. It does not change.
    /// @param targetModel the model of the target document
    /// @param <N> the node type of the target document
    /// @param <M> the type of the target model
    /// @return the root of the changed copy, or the first [MergePatchError]
    public <N, M extends JsonModel<N> & JsonFactory<N>> Result<N, MergePatchError> applyToCopy(N target, M targetModel) {
        return new CopyingMerger<>(model, targetModel).merge(target, document);
    }
}
