package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import ca.marcusdunn.jsonlens.testkit.FaultyModel.Fault;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/// Runs the contract classes on models with a fault, and checks that the contract tests fail.
///
/// The contract classes below run only inside [#failures(Class)]. In the normal test run, JUnit
/// skips them.
@Requirement("lib/testkit-contracts")
class BrokenModelTest {

    private static volatile boolean running;

    static boolean running() {
        return running;
    }

    /// Runs a contract class, and returns the names of the tests that fail.
    private static synchronized Set<String> failures(Class<?> contract) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(contract)).build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        running = true;
        try {
            LauncherFactory.create().execute(request, listener);
        } finally {
            running = false;
        }
        Set<String> names = new TreeSet<>();
        for (TestExecutionSummary.Failure failure : listener.getSummary().getFailures()) {
            names.add(failure.getTestIdentifier().getDisplayName());
        }
        assertTrue(listener.getSummary().getTestsSucceededCount() > 0, contract::getName);
        return names;
    }

    /// The contract on a model with a fault.
    abstract static class Broken extends JsonEditorContract<Object, FaultyModel> {
        private final FaultyModel model;

        Broken(Fault fault) {
            this.model = new FaultyModel(fault);
        }

        @Override
        protected FaultyModel factory() {
            return model;
        }

        @Override
        protected Object parse(String json) {
            return Documents.collections(json);
        }

        @Override
        protected int randomDocuments() {
            return 10;
        }
    }

    @EnabledIf("ca.marcusdunn.jsonlens.testkit.BrokenModelTest#running")
    static final class TooManyMembers extends Broken {
        TooManyMembers() {
            super(Fault.MEMBER_COUNT_IS_TOO_LARGE);
        }
    }

    @EnabledIf("ca.marcusdunn.jsonlens.testkit.BrokenModelTest#running")
    static final class DoubleComparisons extends Broken {
        DoubleComparisons() {
            super(Fault.COMPARES_AS_DOUBLES);
        }
    }

    @EnabledIf("ca.marcusdunn.jsonlens.testkit.BrokenModelTest#running")
    static final class AlwaysEqual extends Broken {
        AlwaysEqual() {
            super(Fault.ALWAYS_EQUAL);
        }
    }

    @EnabledIf("ca.marcusdunn.jsonlens.testkit.BrokenModelTest#running")
    static final class MissingElements extends Broken {
        MissingElements() {
            super(Fault.LAST_ELEMENT_IS_MISSING);
        }
    }

    @EnabledIf("ca.marcusdunn.jsonlens.testkit.BrokenModelTest#running")
    static final class WellFormed extends Broken {
        WellFormed() {
            super(Fault.NONE);
        }
    }

    @Test
    void aWellFormedModelPassesEachContractTest() {
        assertEquals(Set.of(), failures(WellFormed.class));
    }

    @Test
    void aWrongMemberCountFailsTheContract() {
        Set<String> failed = failures(TooManyMembers.class);
        assertTrue(failed.contains("readsObjectMembers()"), failed::toString);
        assertTrue(failed.contains("verifierFindsNoViolations()"), failed::toString);
    }

    @Test
    void aComparisonOfDoublesFailsTheContract() {
        Set<String> failed = failures(DoubleComparisons.class);
        assertTrue(failed.contains("comparesNumbersAsTheirExactValues()"), failed::toString);
        assertTrue(failed.contains("verifierFindsNoViolations()"), failed::toString);
    }

    @Test
    void aWrongEqualityFailsTheContractAndTheSuite() {
        Set<String> failed = failures(AlwaysEqual.class);
        assertTrue(failed.contains("differentValuesAreNotEqual()"), failed::toString);
        // The filters of the JSONPath suite compare with equal.
        assertTrue(failed.contains("filter, deep equality, arrays"), failed::toString);
    }

    @Test
    void aMissingElementFailsTheContractAndTheSuites() {
        Set<String> failed = failures(MissingElements.class);
        assertTrue(failed.contains("readsArrayElements()"), failed::toString);
        // A query of the JSONPath suite that selects each element.
        assertTrue(failed.contains("basic, wildcard selector, array data"), failed::toString);
    }
}
