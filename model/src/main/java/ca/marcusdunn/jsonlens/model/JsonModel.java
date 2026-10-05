package ca.marcusdunn.jsonlens.model;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// Gives jsonlens read access to JSON values in the caller's own representation.
///
/// jsonlens has no JSON value types. A caller implements this interface for the node type
/// `N` of their JSON library, for example `JsonNode` of Jackson,
/// `JsonElement` of Gson, or `Object` for `Map` and `List` values.
/// The evaluator then walks the caller's values through this interface. It does not copy or
/// convert the values, and the result nodes are the caller's own node instances.
///
/// ## Contract
///
/// A model is _well-formed_ if it obeys the rules below. For a well-formed model,
/// jsonlens never throws an exception.
///
/// jsonlens promises:
///
/// - **No null arguments.** jsonlens never calls a method of a model with a `null`
///   argument.
/// - **Kinds.** jsonlens calls a method that is specific to a kind only for a node of
///   that kind. For example, it calls [#arrayLength(Object)] only when
///   [#kind(Object)] gives [JsonKind#ARRAY].
///
/// A well-formed model obeys these rules:
///
/// - **No exceptions.** No method throws an exception.
/// - **No null results.** No method returns `null`, and each node is a non-null
///   reference. A missing element or member is [Maybe.None]. JSON `null` is a
///   present node of kind [JsonKind#NULL]. A model for a representation that uses
///   Java `null` for JSON `null`, such as `Map` and `List`, must
///   use a sentinel object.
/// - **Stable values.** The value of a node does not change during an evaluation.
/// - **Equal nodes.** A model can make a new node object each time it reads a value, so two
///   reads of the same value can give two objects. The two nodes must be equal by `equals`, and
///   have the same `hashCode`. jsonlens does not compare nodes by reference.
/// - **Thread safety.** If more than one thread uses the same model, the model is safe
///   for concurrent reads. A model without state obeys this rule.
///
/// ## Methods
///
/// A model must implement six methods. The other methods have defaults that use the six, so a
/// model overrides them only to be faster. An override must give the same result as the default.
/// The test kit `jsonlens-model-testkit` checks this for each model.
///
/// | Method | For kind | Implement | Use in the evaluator |
/// |---|---|---|---|
/// | [#kind(Object)] | all | required | the kind of each node |
/// | [#arrayLength(Object)] | [JsonKind#ARRAY] | required | index and slice selectors, `length()` |
/// | [#element(Object, int)] | [JsonKind#ARRAY] | required | index, slice, and wildcard selectors |
/// | [#memberCursor(Object)] | [JsonKind#OBJECT] | required | wildcard, filter, and descendant segments, equality |
/// | [#stringValue(Object)] | [JsonKind#STRING] | required | comparisons, `length()`, `match()`, `search()` |
/// | [#numberValue(Object)] | [JsonKind#NUMBER] | required | comparisons |
/// | [#memberCount(Object)] | [JsonKind#OBJECT] | default: a walk of the cursor | `length()`, object equality |
/// | [#member(Object, JsonString)] | [JsonKind#OBJECT] | default: a walk of the cursor | name selectors, singular queries |
/// | [#members(Object)] | [JsonKind#OBJECT] | default: a stream over the cursor | — |
/// | [#compareNumbers(JsonNumber, JsonNumber)] | numbers | default: the exact values | comparisons, equality |
/// | [#equal(Object, Object)] | all | default: no recursion | equality of objects and arrays |
/// | [#hasDuplicate(Object, JsonString)] | [JsonKind#OBJECT] | default: a walk of the cursor | JSON Pointer and JSON Patch |
///
/// ## Comparison of numbers
///
/// The model owns the order of numbers, as a `Comparator` does. One method sees both numbers, so
/// a model can compare two numbers of its own representation without conversion. The default
/// method compares the [exact values][JsonNumber#exactValue()], so a model does not have to
/// override it. An override must obey one invariant: for all numbers `a` and `b`,
/// `compareNumbers(a, b)` has the same sign as `a.exactValue().compareTo(b.exactValue())`.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CustomModelSnippets region=compare-numbers}
///
/// ## Example
///
/// This model reads values made of `Map`, `List`, `String`, `Number`, and `Boolean`. Java `null`
/// becomes a sentinel object, because a node must not be `null`:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CustomModelSnippets region=model}
///
/// Give the model to the evaluator with the root value:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CustomModelSnippets region=use-model}
///
/// The module `ca.marcusdunn.jsonlens.jackson` has a model for Jackson 3, and the Kotlin module
/// `jsonlens-kotlinx-serialization` has a model for kotlinx.serialization.
///
/// @param <N> the node type of the caller's JSON representation
public interface JsonModel<N> {

    /// Returns the kind of a node.
    ///
    /// @param node a node
    /// @return the kind of the node
    JsonKind kind(N node);

    /// Returns the number of elements in an array.
    ///
    /// @param array a node of kind [JsonKind#ARRAY]
    /// @return the number of elements, zero or more
    int arrayLength(N array);

    /// Returns the element at a zero-based index.
    ///
    /// @param array a node of kind [JsonKind#ARRAY]
    /// @param index a zero-based index. It can be outside the array.
    /// @return the element, or [Maybe.None] if the index is outside the array
    Maybe<N> element(N array, int index);

    /// Returns a cursor over the members of an object.
    ///
    /// The cursor is the one way to read the members: the default methods for objects use it.
    /// The order of the cursor is the order in which the evaluator selects the children of the
    /// object. RFC 9535 does not specify this order. The order must be the same each time the
    /// method is called for the same object. The cursor gives each member, also a member with
    /// the name of an earlier member.
    ///
    /// The names can read the representation directly, as [#stringValue(Object)] does. The
    /// evaluator keeps a name in the Normalized Path of each result node, so a name must stay valid
    /// while the values of the model are valid.
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @return a new cursor before the first member
    MemberCursor<N> memberCursor(N object);

    /// Returns the number of members in an object.
    ///
    /// The default method counts the members of the cursor. A model overrides it if it knows the
    /// number without a walk. The result must be the number of members of
    /// [#memberCursor(Object)].
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @return the number of members, zero or more
    default int memberCount(N object) {
        MemberCursor<N> cursor = memberCursor(object);
        int count = 0;
        while (cursor.next()) {
            count++;
        }
        return count;
    }

    /// Returns the value of the member with a given name.
    ///
    /// Names are equal only if they have the same Unicode scalar values. The model must not
    /// apply Unicode normalization or case folding (RFC 9535, Section 2.3.1.2).
    ///
    /// The evaluator gives a name from the query, or a name from the cursor of this model or
    /// another model.
    ///
    /// The default method walks the cursor, and gives the value of the first member with the
    /// name. A model overrides it to find the member faster, for example in a hash map. The
    /// result must be equal to the result of the default method.
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @param name a member name
    /// @return the member value, or [Maybe.None] if the object has no member with the
    /// name
    default Maybe<N> member(N object, JsonString name) {
        MemberCursor<N> cursor = memberCursor(object);
        while (cursor.next()) {
            if (JsonString.equal(cursor.name(), name)) {
                return Maybe.some(cursor.value());
            }
        }
        return Maybe.none();
    }

    /// Returns the members of an object as name/value properties, in the order of the cursor.
    ///
    /// The default method makes a stream over [#memberCursor(Object)]. A model does not have to
    /// override it.
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @return a new stream of the members
    default Stream<Property<N>> members(N object) {
        return StreamSupport.stream(new CursorSpliterator<>(memberCursor(object)), false);
    }

    /// Returns the value of a string.
    ///
    /// The result can read the representation directly. For a `String`, use [JsonString#of(String)].
    ///
    /// @param string a node of kind [JsonKind#STRING]
    /// @return the value of the string
    JsonString stringValue(N string);

    /// Returns the value of a number.
    ///
    /// The model chooses the representation. For the common types, use the factories of
    /// [JsonNumber], for example [JsonNumber#of(long)].
    ///
    /// @param number a node of kind [JsonKind#NUMBER]
    /// @return the value of the number
    JsonNumber numberValue(N number);

    /// Compares two numbers by their mathematical values (RFC 9535, Section 2.3.5.2.2).
    ///
    /// The numbers come from [#numberValue(Object)] of this model, or from the factories of
    /// [JsonNumber], for example for a number literal of the query. The default method compares
    /// the [exact values][JsonNumber#exactValue()], with fast paths that need no conversion: for integers in
    /// the range of `long` (from [JsonNumber#of(long)] or the other factories), for numbers from
    /// [JsonNumber#of(double)], and for an integer up to 2^53 and a `double`.
    ///
    /// An override compares the numbers of its own representation, and calls the default method
    /// (`JsonModel.super.compareNumbers(a, b)`) for all other numbers. The result must have the
    /// same sign as `a.exactValue().compareTo(b.exactValue())`.
    ///
    /// @param a a number
    /// @param b another number
    /// @return a negative value, zero, or a positive value if `a` is less than, equal to, or
    ///     greater than `b`
    default int compareNumbers(JsonNumber a, JsonNumber b) {
        return Numbers.compare(a, b);
    }
    /// Tells if two values are equal, as JSON values.
    ///
    /// The rules are the same in RFC 9535 (Section 2.3.5.2.2, the comparison `==`) and RFC 6902
    /// (Section 4.6, the operation `test`). The values must have the same kind, and:
    ///
    /// - two strings have the same Unicode scalar values ([JsonString#equal(JsonString, JsonString)]);
    /// - two numbers have the same mathematical value ([#compareNumbers(JsonNumber, JsonNumber)]);
    /// - two arrays have the same number of elements, and the elements at each index are equal;
    /// - two objects have the same number of members, and for each member of the first object,
    ///   the second object has a member with the same name and an equal value. The order of the
    ///   members has no effect.
    ///
    /// The default method uses no recursion, so a deep value cannot overflow the stack. A model can
    /// override it, for example to compare its own nodes faster. The result must be the same as
    /// the result of the default method.
    ///
    /// @param a a value
    /// @param b another value
    /// @return `true` if the values are equal
    default boolean equal(N a, N b) {
        return equal(this, a, this, b);
    }

    /// Tells if two values of two models are equal, as JSON values.
    ///
    /// The rules are the rules of [#equal(Object, Object)]. The models can be different, for
    /// example the model of a Jackson tree and the model of a JSON Patch document in a
    /// memory-mapped file. The method reads both values directly and makes no copy. It compares
    /// numbers with [#compareNumbers(JsonNumber, JsonNumber)] of the first model, and member names
    /// by their Unicode scalar values.
    ///
    /// @param firstModel the model of the first value
    /// @param first the first value
    /// @param secondModel the model of the second value
    /// @param second the second value
    /// @param <A> the node type of the first model
    /// @param <B> the node type of the second model
    /// @return `true` if the values are equal
    static <A, B> boolean equal(JsonModel<A> firstModel, A first, JsonModel<B> secondModel, B second) {
        return new Equality<>(firstModel, secondModel).equal(first, second);
    }

    /// Tells if an object has more than one member with a name.
    ///
    /// JSON permits duplicate member names, but their meaning is not defined (RFC 8259, Section
    /// 4). RFC 6901 (JSON Pointer) and RFC 6902 (JSON Patch) must detect them: a pointer to a
    /// name that is not unique fails, and an operation must have exactly one `op` member.
    ///
    /// The default method walks the cursor and counts the members with the name. A model whose
    /// objects cannot hold duplicate names, for example a model of `Map` values, can override it
    /// to return `false` without a walk. The result must be the same as the result of the default
    /// method.
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @param name a member name
    /// @return `true` if the object has two or more members with the name
    default boolean hasDuplicate(N object, JsonString name) {
        MemberCursor<N> cursor = memberCursor(object);
        boolean found = false;
        while (cursor.next()) {
            if (JsonString.equal(cursor.name(), name)) {
                if (found) {
                    return true;
                }
                found = true;
            }
        }
        return false;
    }
}
