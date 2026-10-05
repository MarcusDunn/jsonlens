/// Tests for a [ca.marcusdunn.jsonlens.model.JsonModel]: [ca.marcusdunn.jsonlens.testkit.ModelVerifier]
/// checks the rules of the model on any document, the contract classes run fixed cases and the
/// verifier as JUnit tests, and [ca.marcusdunn.jsonlens.testkit.ComplianceKit] runs the RFC 9535 and
/// RFC 6902 test suites on the model.
///
/// ## Dependencies
///
/// The module requires JUnit Jupiter and the jsonlens modules that run the suites. Use it only
/// in tests.
module ca.marcusdunn.jsonlens.testkit {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;
    requires transitive org.junit.jupiter.api;
    requires ca.marcusdunn.jsonlens.path.core;
    requires ca.marcusdunn.jsonlens.mapped;
    requires ca.marcusdunn.jsonlens.path.parser;
    requires ca.marcusdunn.jsonlens.path.evaluator;
    requires ca.marcusdunn.jsonlens.patch;

    exports ca.marcusdunn.jsonlens.testkit;
    // JUnit calls the test methods of the contract classes by reflection.
    opens ca.marcusdunn.jsonlens.testkit to org.junit.platform.commons;
}
