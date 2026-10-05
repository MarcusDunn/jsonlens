package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The function extensions of an evaluator, by name, and the signatures of all its functions.
 *
 * @param readOnly the read-only extensions
 * @param building the building extensions
 * @param signatures the signatures of the standard functions and of all extensions
 */
record Functions(
        Map<String, FunctionExtension> readOnly,
        Map<String, BuildingFunctionExtension> building,
        Map<String, FunctionSignature> signatures) {

    static final Functions STANDARD = new Functions(Map.of(), Map.of(), standardSignatures());

    /** Checks and registers extensions. */
    static Result<Functions, ExtensionError> of(
            List<FunctionExtension> readOnly, List<BuildingFunctionExtension> building) {
        Map<String, FunctionSignature> signatures = standardSignatures();
        Map<String, FunctionExtension> readOnlyByName = new LinkedHashMap<>();
        for (FunctionExtension extension : readOnly) {
            FunctionSignature signature = extension.signature();
            if (signature.parameterTypes().contains(FunctionType.VALUE)) {
                return Result.err(new ExtensionError.ValueParameter(signature.name()));
            }
            ExtensionError error = register(signature, signatures);
            if (error != null) {
                return Result.err(error);
            }
            readOnlyByName.put(signature.name(), extension);
        }
        Map<String, BuildingFunctionExtension> buildingByName = new LinkedHashMap<>();
        for (BuildingFunctionExtension extension : building) {
            ExtensionError error = register(extension.signature(), signatures);
            if (error != null) {
                return Result.err(error);
            }
            buildingByName.put(extension.signature().name(), extension);
        }
        return Result.ok(new Functions(
                Collections.unmodifiableMap(readOnlyByName),
                Collections.unmodifiableMap(buildingByName),
                Collections.unmodifiableMap(signatures)));
    }

    /** Adds a signature, or returns the error. */
    private static @Nullable ExtensionError register(FunctionSignature signature, Map<String, FunctionSignature> signatures) {
        String name = signature.name();
        if (!FunctionSignature.isValidName(name)) {
            return new ExtensionError.InvalidName(name);
        }
        if (signatures.putIfAbsent(name, signature) != null) {
            return new ExtensionError.DuplicateName(name);
        }
        return null;
    }

    private static Map<String, FunctionSignature> standardSignatures() {
        Map<String, FunctionSignature> signatures = new LinkedHashMap<>();
        FunctionSignature.STANDARD.forEach(s -> signatures.put(s.name(), s));
        return signatures;
    }
}
