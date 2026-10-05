package ca.marcusdunn.jsonlens.path.core.query;

import java.util.List;

/// A segment: it applies its selectors to the children or descendants of a node (RFC 9535, Section 2.5).
///
/// For each input node, a segment concatenates the results of its selectors in their order. The
/// segment result is the concatenation of these results, in the order of the input nodes.
///
/// | Segment | Query text | Applies its selectors to |
/// |---|---|---|
/// | [Child] | `[0, 'a']`, `.a`, `.*` | the input node |
/// | [Descendant] | `..[0, 'a']`, `..a`, `..*` | the input node and all its descendants, in pre-order |
///
/// For example, on `{"a": {"a": 1}}`, the query `$..a` selects `{"a": 1}` and then `1`.
public sealed interface Segment {

    /// Returns the selectors of the segment.
    ///
    /// @return one or more selectors, in order
    List<Selector> selectors();

    /// A child segment: `[<selectors>]`, `.name`, or `.*` (Section 2.5.1).
    ///
    /// @param selectors one or more selectors, in order
    record Child(List<Selector> selectors) implements Segment {
        /// Makes a child segment.
        ///
        /// @param selectors one or more selectors, in order
        public Child {
            selectors = List.copyOf(selectors);
        }
    }

    /// A descendant segment: `..[<selectors>]`, `..name`, or `..*` (Section 2.5.2).
    ///
    /// @param selectors one or more selectors, in order
    record Descendant(List<Selector> selectors) implements Segment {
        /// Makes a descendant segment.
        ///
        /// @param selectors one or more selectors, in order
        public Descendant {
            selectors = List.copyOf(selectors);
        }
    }
}
