package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import java.util.List;

/**
 * The part of the {@link JsonFactory} of the model that the evaluator uses, and a way to call
 * building extensions with the model.
 *
 * <p>A {@link BuildingEvaluator} makes it, because only the building evaluator knows that the
 * model also implements {@link JsonFactory}. The evaluator builds only primitive values: the
 * literals and computed numbers that it gives to building extensions as arguments.
 *
 * @param <N> the node type
 */
interface Building<N> {

    /** Applies a building extension with the model. */
    Instance<N> apply(BuildingFunctionExtension extension, List<Instance<N>> arguments);

    N string(JsonString value);

    /** Builds a number, or gives none if the representation cannot hold it. */
    Maybe<N> number(JsonNumber value);

    N bool(boolean value);

    N nullValue();
}
