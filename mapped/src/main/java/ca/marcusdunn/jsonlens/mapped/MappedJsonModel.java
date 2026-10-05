package ca.marcusdunn.jsonlens.mapped;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;

/// The read-only model of [MappedJson]. Each node knows its [MappedJson], so one model serves all.
///
/// An array or an object has an entry in [MappedJson#children]: the number of children, then the
/// element indexes, or a pair of a name offset and a value index for each member.
enum MappedJsonModel implements JsonModel<MappedNode> {
    INSTANCE;

    @Override
    public JsonKind kind(MappedNode node) {
        return node.json.kind(node.index);
    }

    @Override
    public int arrayLength(MappedNode array) {
        return count(array);
    }

    @Override
    public Maybe<MappedNode> element(MappedNode array, int index) {
        MappedJson json = array.json;
        if (index < 0 || index >= count(array)) {
            return Maybe.none();
        }
        return Maybe.some(json.node(json.children[json.slots[array.index] + 1 + index]));
    }

    @Override
    public int memberCount(MappedNode object) {
        return count(object);
    }

    /// The number of children of an array or an object.
    private static int count(MappedNode container) {
        return container.json.children[container.json.slots[container.index]];
    }

    /// The position of the first member pair of an object in [MappedJson#children].
    private static int firstPair(MappedNode object) {
        return object.json.slots[object.index] + 1;
    }

    /// The position after the last member pair of an object.
    private static int endOfPairs(MappedNode object) {
        return firstPair(object) + 2 * count(object);
    }

    @Override
    public Maybe<MappedNode> member(MappedNode object, JsonString name) {
        MappedJson json = object.json;
        int end = endOfPairs(object);
        for (int k = firstPair(object); k < end; k += 2) {
            if (JsonString.equal(new MappedString(json.data, json.children[k]), name)) {
                return Maybe.some(json.node(json.children[k + 1]));
            }
        }
        return Maybe.none();
    }

    @Override
    public MemberCursor<MappedNode> memberCursor(MappedNode object) {
        return new MappedCursor(object.json, firstPair(object), endOfPairs(object));
    }

    /// A cursor over the members of an object: a position in [MappedJson#children]. It decodes no
    /// name: a name is a view of the bytes.
    private static final class MappedCursor implements MemberCursor<MappedNode> {
        private final MappedJson json;
        private final int end;
        private int next;
        private int current;

        MappedCursor(MappedJson json, int start, int end) {
            this.json = json;
            this.next = start;
            this.end = end;
        }

        @Override
        public boolean next() {
            if (next == end) {
                return false;
            }
            current = next;
            next += 2;
            return true;
        }

        @Override
        public JsonString name() {
            return new MappedString(json.data, json.children[current]);
        }

        @Override
        public MappedNode value() {
            return json.node(json.children[current + 1]);
        }
    }

    @Override
    public JsonString stringValue(MappedNode string) {
        return new MappedString(string.json.data, string.json.slots[string.index]);
    }

    @Override
    public JsonNumber numberValue(MappedNode number) {
        return new MappedNumber(number.json.data, number.json.slots[number.index]);
    }
}
