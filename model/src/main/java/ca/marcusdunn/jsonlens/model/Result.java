package ca.marcusdunn.jsonlens.model;

import java.util.function.Function;

/// The result of an operation that can fail: a success value ([Ok]) or an error value ([Err]).
///
/// jsonlens reports errors with this type, not with exceptions. The parser, the evaluator, and
/// the registration of function extensions all return a `Result`.
///
/// ## Examine a result
///
/// `Result` is sealed, so a `switch` with record patterns must handle both cases. The compiler
/// tells you if a case is missing:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=result}
///
/// ## Make a result
///
/// Use [#ok(Object)] and [#err(Object)]. The error type is usually a sealed interface of records:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=result-producer}
///
/// ## Operations
///
/// | Method | On [Ok] | On [Err] |
/// |---|---|---|
/// | [#map(Function)] | applies the function to the value | no change |
/// | [#mapErr(Function)] | no change | applies the function to the error |
/// | [#flatMap(Function)] | applies an operation that can fail | no change |
/// | [#fold(Function, Function)] | applies the first function | applies the second function |
/// | [#orElse(Object)] | gives the value | gives the fallback value |
///
/// [#flatMap(Function)] chains operations that can fail, for example a parse and an evaluation:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.QuickStartSnippets region=chained}
///
/// @param <V> the type of the success value
/// @param <E> the type of the error value
public sealed interface Result<V, E> {

    /// A success value.
    ///
    /// @param value the value
    /// @param <V> the type of the success value
    /// @param <E> the type of the error value
    record Ok<V, E>(V value) implements Result<V, E> {}

    /// An error value.
    ///
    /// @param error the error
    /// @param <V> the type of the success value
    /// @param <E> the type of the error value
    record Err<V, E>(E error) implements Result<V, E> {}

    /// Makes a success result.
    ///
    /// @param value the value
    /// @param <V> the type of the success value
    /// @param <E> the type of the error value
    /// @return a success result
    static <V, E> Result<V, E> ok(V value) {
        return new Ok<>(value);
    }

    /// Makes an error result.
    ///
    /// @param error the error
    /// @param <V> the type of the success value
    /// @param <E> the type of the error value
    /// @return an error result
    static <V, E> Result<V, E> err(E error) {
        return new Err<>(error);
    }

    /// Returns true for a success result.
    ///
    /// @return true if this is an [Ok]
    default boolean isOk() {
        return this instanceof Ok;
    }

    /// Returns true for an error result.
    ///
    /// @return true if this is an [Err]
    default boolean isErr() {
        return this instanceof Err;
    }

    /// Changes the success value. An error result does not change.
    ///
    /// @param mapper the function for the success value
    /// @param <U> the new type of the success value
    /// @return the changed result
    default <U> Result<U, E> map(Function<? super V, ? extends U> mapper) {
        return switch (this) {
            case Ok<V, E>(V value) -> new Ok<>(mapper.apply(value));
            case Err<V, E>(E error) -> new Err<>(error);
        };
    }

    /// Changes the error value. A success result does not change.
    ///
    /// @param mapper the function for the error value
    /// @param <F> the new type of the error value
    /// @return the changed result
    default <F> Result<V, F> mapErr(Function<? super E, ? extends F> mapper) {
        return switch (this) {
            case Ok<V, E>(V value) -> new Ok<>(value);
            case Err<V, E>(E error) -> new Err<>(mapper.apply(error));
        };
    }

    /// Applies an operation that can fail to the success value. An error result does not change.
    ///
    /// @param mapper the operation for the success value
    /// @param <U> the type of the success value of the operation
    /// @return the result of the operation, or this error
    default <U> Result<U, E> flatMap(Function<? super V, Result<U, E>> mapper) {
        return switch (this) {
            case Ok<V, E>(V value) -> mapper.apply(value);
            case Err<V, E>(E error) -> new Err<>(error);
        };
    }

    /// Changes the success value or the error value into one value.
    ///
    /// @param onOk the function for a success value
    /// @param onErr the function for an error value
    /// @param <R> the type of the value
    /// @return the value from the applicable function
    default <R> R fold(Function<? super V, ? extends R> onOk, Function<? super E, ? extends R> onErr) {
        return switch (this) {
            case Ok<V, E>(V value) -> onOk.apply(value);
            case Err<V, E>(E error) -> onErr.apply(error);
        };
    }

    /// Returns the success value, or a fallback value for an error result.
    ///
    /// @param fallback the value for an error result
    /// @return the success value or the fallback value
    default V orElse(V fallback) {
        return switch (this) {
            case Ok<V, E>(V value) -> value;
            case Err<V, E> err -> fallback;
        };
    }
}
