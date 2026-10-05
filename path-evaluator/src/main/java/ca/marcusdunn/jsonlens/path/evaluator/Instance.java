package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import java.util.List;

/// An instance of a declared type: a function argument or a function result
/// (RFC 9535, Section 2.4.1, Table 13).
///
/// | Declared type | Record | Example of a source in a query |
/// |---|---|---|
/// | ValueType | [ValueInstance] | `'text'`, `@.price`, `length(@)` |
/// | LogicalType | [LogicalInstance] | `@.a == 1`, `match(@, 'a.*')`, an existence test `@.a` |
/// | NodesType | [NodesInstance] | `@.*`, `$..book` |
///
/// A [FunctionExtension] receives instances and gives an instance:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=extension}
///
/// @param <N> the node type of the JSON model
public sealed interface Instance<N> {

    /// An instance of ValueType: a node, or Nothing.
    ///
    /// @param value the node, or [Maybe.None] for Nothing
    /// @param <N> the node type of the JSON model
    record ValueInstance<N>(Maybe<N> value) implements Instance<N> {}

    /// An instance of LogicalType: LogicalTrue or LogicalFalse.
    ///
    /// @param value true for LogicalTrue
    /// @param <N> the node type of the JSON model
    record LogicalInstance<N>(boolean value) implements Instance<N> {}

    /// An instance of NodesType: a nodelist.
    ///
    /// @param nodes the nodes
    /// @param <N> the node type of the JSON model
    record NodesInstance<N>(List<Node<N>> nodes) implements Instance<N> {
        /// Makes a nodelist instance.
        ///
        /// @param nodes the nodes
        public NodesInstance {
            nodes = List.copyOf(nodes);
        }
    }
}
