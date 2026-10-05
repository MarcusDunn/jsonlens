package ca.marcusdunn.jsonlens.path.core.query;

import ca.marcusdunn.jsonlens.model.Maybe;

/// A selector in a segment (RFC 9535, Section 2.3). A selector selects zero or more children of a
/// node.
///
/// | Selector | Query text | Selects from `{"a": [10, 20, 30]}`, at `$.a` |
/// |---|---|---|
/// | [Name] | `$['a']`, `$.a` | the array (from the root) |
/// | [Wildcard] | `$.a[*]`, `$.a.*` | `10`, `20`, `30` |
/// | [Index] | `$.a[1]`, `$.a[-1]` | `20`; `30` |
/// | [Slice] | `$.a[0:2]`, `$.a[::-1]` | `10`, `20`; `30`, `20`, `10` |
/// | [Filter] | `$.a[?@ > 15]` | `20`, `30` |
///
/// A selector that does not apply to a node selects nothing. It is not an error. For example, an
/// index selector selects nothing from an object, or from an array that is too short.
public sealed interface Selector {

    /// A name selector: `'name'` or `"name"` (Section 2.3.1). It selects the value of the object
    /// member with the name.
    ///
    /// The shorthand `.name` is the same selector. The parser replaces all escapes, so `$['a\'b']`
    /// and `$["a'b"]` give the same selector, with the name `a'b`.
    ///
    /// @param name the member name, with all escapes replaced
    record Name(String name) implements Selector {}

    /// The wildcard selector: `*` (Section 2.3.2). It selects all children of an object or an
    /// array.
    record Wildcard() implements Selector {}

    /// An index selector (Section 2.3.3). It selects one element of an array.
    ///
    /// | Index | Element of `["a", "b", "c"]` |
    /// |---|---|
    /// | `0` | `"a"` |
    /// | `2` | `"c"` |
    /// | `-1` | `"c"` |
    /// | `3`, `-4` | nothing |
    ///
    /// @param index the index. A negative index counts from the end of the array. A valid index is
    ///     in the I-JSON range `[-(2^53)+1, (2^53)-1]`.
    record Index(long index) implements Selector {}

    /// An array slice selector: `start:end:step` (Section 2.3.4). It selects the elements from
    /// `start` up to, but not including, `end`, in increments of `step`.
    ///
    /// | Slice | Elements of `["a", "b", "c", "d", "e"]` |
    /// |---|---|
    /// | `1:3` | `"b"`, `"c"` |
    /// | `:2` | `"a"`, `"b"` |
    /// | `-2:` | `"d"`, `"e"` |
    /// | `::2` | `"a"`, `"c"`, `"e"` |
    /// | `::-1` | `"e"`, `"d"`, `"c"`, `"b"`, `"a"` |
    /// | `::0` | nothing |
    ///
    /// The default step is `1`. The default start and end depend on the sign of the step.
    ///
    /// @param start the start, if the query gives it
    /// @param end the end, if the query gives it
    /// @param step the step, if the query gives it
    record Slice(Maybe<Long> start, Maybe<Long> end, Maybe<Long> step) implements Selector {}

    /// A filter selector: `?<logical-expr>` (Section 2.3.5). It selects each child for which the
    /// expression is true. In the expression, `@` is the child.
    ///
    /// For example, `$.books[?@.price < 10 && @.isbn]` selects the books with a price less than
    /// 10 and an `isbn` member.
    ///
    /// @param expression the filter expression
    record Filter(LogicalExpression expression) implements Selector {}
}
