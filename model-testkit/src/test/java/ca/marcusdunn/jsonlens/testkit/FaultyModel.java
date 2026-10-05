package ca.marcusdunn.jsonlens.testkit;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// The Java collections model with one fault. Each fault breaks one rule of the contract.
///
/// A node is a value of [JavaCollectionsModel], or a [Box] around one. A box is equal only to itself,
/// so a fault that boxes a value gives nodes that are not equal for the same value.
final class FaultyModel implements JsonModel<Object>, JsonFactory<Object>, JsonEditor<Object> {

    enum Fault {
        NONE,
        KIND_IS_NULL,
        STRING_VALUE_THROWS,
        LAST_ELEMENT_IS_MISSING,
        NEGATIVE_INDEX_IS_PRESENT,
        ELEMENTS_ARE_NEW_NODES,
        ELEMENTS_HAVE_NEW_HASH_CODES,
        CURSOR_STARTS_AGAIN,
        CURSOR_ORDER_CHANGES,
        CURSOR_VALUES_ARE_NEW_NODES,
        MEMBER_COUNT_IS_TOO_LARGE,
        MEMBER_GIVES_ANOTHER_VALUE,
        MEMBER_NEEDS_OWN_NAMES,
        MEMBER_FINDS_ANY_NAME,
        MEMBERS_ARE_REVERSED,
        MEMBERS_ARE_NEW_NODES,
        MEMBERS_LOSE_THE_LAST,
        ALWAYS_DUPLICATE,
        STRING_VALUE_CHANGES,
        NUMBER_VALUE_CHANGES,
        COMPARES_AS_DOUBLES,
        GREATER_COMPARES_AS_EQUAL,
        SELF_COMPARES_AS_GREATER,
        LITERALS_COMPARE_AS_SMALLER,
        ALWAYS_EQUAL,
        CONTAINERS_EQUAL_SCALARS
    }

    /// A node that is equal only to itself.
    static class Box {
        final Object value;

        Box(Object value) {
            this.value = value;
        }
    }

    /// A node that is equal to each node with the same value, but with a new hash code each time.
    static final class HashBox extends Box {
        HashBox(Object value) {
            super(value);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof HashBox box && box.value.equals(value);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    /// A name in a representation of the model's own.
    private record OwnName(JsonString name) implements JsonString {
        @Override
        public java.util.PrimitiveIterator.OfInt scalarValues() {
            return name.scalarValues();
        }
    }

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    private final Fault fault;
    private int reads;

    FaultyModel(Fault fault) {
        this.fault = fault;
    }

    private static Object v(Object node) {
        return node instanceof Box box ? box.value : node;
    }

    /// Changes every second time: for a fault that gives a different value for a second read.
    private boolean second() {
        return reads++ % 2 == 1;
    }

    @Override
    @SuppressWarnings("NullAway") // The fault returns null on purpose.
    public JsonKind kind(Object node) {
        JsonKind kind = MODEL.kind(v(node));
        return fault == Fault.KIND_IS_NULL && kind == JsonKind.STRING ? nothing() : kind;
    }

    @SuppressWarnings({"NullAway", "TypeParameterUnusedInFormals"})
    private static <T> T nothing() {
        @Nullable T none = null;
        return none;
    }

    @Override
    public int arrayLength(Object array) {
        return MODEL.arrayLength(v(array));
    }

    @Override
    public Maybe<Object> element(Object array, int index) {
        int length = MODEL.arrayLength(v(array));
        if (fault == Fault.LAST_ELEMENT_IS_MISSING && index == length - 1) {
            return Maybe.none();
        }
        if (fault == Fault.NEGATIVE_INDEX_IS_PRESENT && index < 0 && length > 0) {
            return MODEL.element(v(array), 0);
        }
        Maybe<Object> element = MODEL.element(v(array), index);
        return switch (fault) {
            case ELEMENTS_ARE_NEW_NODES -> element.map(Box::new);
            case ELEMENTS_HAVE_NEW_HASH_CODES -> element.map(HashBox::new);
            default -> element;
        };
    }

    @Override
    public MemberCursor<Object> memberCursor(Object object) {
        List<Property<Object>> members = new ArrayList<>(MODEL.members(v(object)).toList());
        if (fault == Fault.CURSOR_ORDER_CHANGES && second()) {
            Collections.reverse(members);
        }
        if (fault == Fault.CURSOR_VALUES_ARE_NEW_NODES) {
            members.replaceAll(member -> new Property<>(member.name(), new Box(member.value())));
        }
        if (fault == Fault.MEMBER_NEEDS_OWN_NAMES) {
            members.replaceAll(member -> new Property<>(new OwnName(member.name()), member.value()));
        }
        MemberCursor<Object> cursor = MemberCursor.of(members);
        if (fault != Fault.CURSOR_STARTS_AGAIN) {
            return cursor;
        }
        return new MemberCursor<>() {
            private MemberCursor<Object> current = cursor;

            @Override
            public boolean next() {
                if (current.next()) {
                    return true;
                }
                current = MemberCursor.of(members);
                return false;
            }

            @Override
            public JsonString name() {
                return current.name();
            }

            @Override
            public Object value() {
                return current.value();
            }
        };
    }

    @Override
    public int memberCount(Object object) {
        return MODEL.memberCount(v(object)) + (fault == Fault.MEMBER_COUNT_IS_TOO_LARGE ? 1 : 0);
    }

    @Override
    public Maybe<Object> member(Object object, JsonString name) {
        return switch (fault) {
            case MEMBER_GIVES_ANOTHER_VALUE -> MODEL.member(v(object), name).map(value -> "another");
            case MEMBER_NEEDS_OWN_NAMES -> name instanceof OwnName ? MODEL.member(v(object), name) : Maybe.none();
            case MEMBER_FINDS_ANY_NAME -> MODEL.member(v(object), name).isSome() ? MODEL.member(v(object), name) : Maybe.some("any");
            default -> MODEL.member(v(object), name);
        };
    }

    @Override
    public Stream<Property<Object>> members(Object object) {
        List<Property<Object>> members = new ArrayList<>(MODEL.members(v(object)).toList());
        if (fault == Fault.MEMBERS_LOSE_THE_LAST) {
            members.removeLast();
        }
        if (fault == Fault.MEMBERS_ARE_REVERSED) {
            Collections.reverse(members);
        }
        if (fault == Fault.MEMBERS_ARE_NEW_NODES) {
            members.replaceAll(member -> new Property<>(member.name(), new Box(member.value())));
        }
        return members.stream();
    }

    @Override
    public boolean hasDuplicate(Object object, JsonString name) {
        return fault == Fault.ALWAYS_DUPLICATE;
    }

    @Override
    public JsonString stringValue(Object string) {
        if (fault == Fault.STRING_VALUE_THROWS) {
            throw new IllegalStateException("broken");
        }
        JsonString value = MODEL.stringValue(v(string));
        return fault == Fault.STRING_VALUE_CHANGES && second() ? JsonString.of(JsonString.copyOf(value) + "!") : value;
    }

    @Override
    public JsonNumber numberValue(Object number) {
        JsonNumber value = MODEL.numberValue(v(number));
        return fault == Fault.NUMBER_VALUE_CHANGES && second() ? JsonNumber.of(-12345) : value;
    }

    @Override
    @SuppressWarnings("ReferenceEquality") // SELF_COMPARES_AS_GREATER finds the same object on purpose.
    public int compareNumbers(JsonNumber a, JsonNumber b) {
        return switch (fault) {
            case COMPARES_AS_DOUBLES -> Double.compare(asDouble(a), asDouble(b));
            case GREATER_COMPARES_AS_EQUAL -> Math.min(0, JsonModel.super.compareNumbers(a, b));
            case SELF_COMPARES_AS_GREATER -> a == b ? 1 : JsonModel.super.compareNumbers(a, b);
            case LITERALS_COMPARE_AS_SMALLER -> (a.getClass() != b.getClass() && a.exactValue().equals(b.exactValue())) ? 1 : JsonModel.super.compareNumbers(a, b);
            default -> JsonModel.super.compareNumbers(a, b);
        };
    }

    private static double asDouble(JsonNumber number) {
        return Double.parseDouble(number.exactValue().toString());
    }

    @Override
    public boolean equal(Object a, Object b) {
        return switch (fault) {
            case ALWAYS_EQUAL -> true;
            case CONTAINERS_EQUAL_SCALARS -> (container(a) && !container(b)) || JsonModel.super.equal(a, b);
            default -> JsonModel.super.equal(a, b);
        };
    }

    private boolean container(Object node) {
        JsonKind kind = MODEL.kind(v(node));
        return kind == JsonKind.ARRAY || kind == JsonKind.OBJECT;
    }

    // The factory and the editor: as the Java collections model.

    @Override
    public Object string(JsonString value) {
        return MODEL.string(value);
    }

    @Override
    public Maybe<Object> number(JsonNumber value) {
        return MODEL.number(value);
    }

    @Override
    public Object bool(boolean value) {
        return MODEL.bool(value);
    }

    @Override
    public Object nullValue() {
        return MODEL.nullValue();
    }

    @Override
    public Object array(List<Object> elements) {
        return MODEL.array(elements);
    }

    @Override
    public Object object(List<Property<Object>> members) {
        return MODEL.object(members);
    }

    @Override
    public Maybe<Object> putMember(Object object, JsonString name, Object value) {
        return MODEL.putMember(v(object), name, value);
    }

    @Override
    public Maybe<Object> removeMember(Object object, JsonString name) {
        return MODEL.removeMember(v(object), name);
    }

    @Override
    public void insertElement(Object array, int index, Object value) {
        MODEL.insertElement(v(array), index, value);
    }

    @Override
    public Object setElement(Object array, int index, Object value) {
        return MODEL.setElement(v(array), index, value);
    }

    @Override
    public Object removeElement(Object array, int index) {
        return MODEL.removeElement(v(array), index);
    }
}
