package ca.marcusdunn.jsonlens.model;

import java.util.function.Function;
import java.util.function.Supplier;

/// A value that can be absent: [Some] value or [None].
///
/// jsonlens uses this type, not `null` and not [java.util.Optional], for values that can be
/// absent. For example, [ca.marcusdunn.jsonlens.model.JsonModel#member(Object, ca.marcusdunn.jsonlens.model.JsonString)] gives
/// [None] for a member that is not in the object. Unlike `Optional`, `Maybe` is sealed, so a
/// `switch` with record patterns can examine it:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=maybe}
///
/// A [Some] value is never `null`.
///
/// @param <T> the type of the value
public sealed interface Maybe<T> {

    /// A present value.
    ///
    /// @param value the value
    /// @param <T> the type of the value
    record Some<T>(T value) implements Maybe<T> {}

    /// An absent value.
    ///
    /// @param <T> the type of the value
    record None<T>() implements Maybe<T> {}

    /// Makes a present value.
    ///
    /// @param value the value
    /// @param <T> the type of the value
    /// @return a present value
    static <T> Maybe<T> some(T value) {
        return new Some<>(value);
    }

    /// Returns an absent value.
    ///
    /// @param <T> the type of the value
    /// @return an absent value
    static <T> Maybe<T> none() {
        return new None<>();
    }

    /// Returns true for a present value.
    ///
    /// @return true if this is a [Some]
    default boolean isSome() {
        return this instanceof Some;
    }

    /// Returns true for an absent value.
    ///
    /// @return true if this is a [None]
    default boolean isNone() {
        return this instanceof None;
    }

    /// Changes a present value. An absent value does not change.
    ///
    /// @param mapper the function for the value
    /// @param <U> the new type of the value
    /// @return the changed value, or an absent value
    default <U> Maybe<U> map(Function<? super T, ? extends U> mapper) {
        return switch (this) {
            case Some<T>(T value) -> new Some<>(mapper.apply(value));
            case None<T>() -> none();
        };
    }

    /// Applies a function that can give an absent value to a present value.
    ///
    /// @param mapper the function for the value
    /// @param <U> the type of the value of the function
    /// @return the result of the function, or an absent value
    default <U> Maybe<U> flatMap(Function<? super T, Maybe<U>> mapper) {
        return switch (this) {
            case Some<T>(T value) -> mapper.apply(value);
            case None<T>() -> none();
        };
    }

    /// Returns the present value, or a fallback value.
    ///
    /// @param fallback the value for an absent value
    /// @return the present value or the fallback value
    default T orElse(T fallback) {
        return switch (this) {
            case Some<T>(T value) -> value;
            case None<T>() -> fallback;
        };
    }

    /// Returns the present value, or a value from a supplier.
    ///
    /// @param fallback the supplier for an absent value
    /// @return the present value or the supplied value
    default T orElseGet(Supplier<? extends T> fallback) {
        return switch (this) {
            case Some<T>(T value) -> value;
            case None<T>() -> fallback.get();
        };
    }

    /// Makes a result: a success for a present value, or an error for an absent value.
    ///
    /// @param error the supplier of the error for an absent value
    /// @param <E> the type of the error
    /// @return the result
    default <E> Result<T, E> toResult(Supplier<? extends E> error) {
        return switch (this) {
            case Some<T>(T value) -> Result.ok(value);
            case None<T>() -> Result.err(error.get());
        };
    }
}

