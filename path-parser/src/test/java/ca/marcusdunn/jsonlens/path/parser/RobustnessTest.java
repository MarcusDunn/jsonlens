package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.STANDARD;
import static ca.marcusdunn.jsonlens.path.parser.Queries.parse;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Sections 4.1 and 4.2, and the exception-free promise. */
class RobustnessTest {

    /** Valid queries that use all parts of the grammar. */
    static final List<String> CORPUS = List.of(
            "$",
            "$.store.book[0].title",
            "$['store']['book'][-1]",
            "$..author",
            "$.store.*",
            "$..*",
            "$..[0,'a',*]",
            "$[1:3]",
            "$[::-1]",
            "$[ -9007199254740991 : 9007199254740991 : 2 ]",
            "$[\"a\\\"b\\u00e9\\uD83D\\uDE00\"]",
            "$['\\b\\f\\n\\r\\t\\/\\\\\\'\\u001f']",
            "$.é中",
            "$[?@.isbn]",
            "$[?!@.isbn]",
            "$[?@.price < 10 && @.category == 'fiction' || @.price >= 100]",
            "$[?(@.a || @.b) && !(@.c == null)]",
            "$[?@.a == true && @.b != false && @.c <= -1.5e-3 && @.d > 0]",
            "$[?$.limit >= @.price]",
            "$[?length(@.authors) >= 5]",
            "$[?count(@.*.author) >= 5]",
            "$[?match(@.date, '1974-05-..')]",
            "$[?search(@.author, '[BR]ob')]",
            "$[?value(@..color) == \"red\"]",
            "$[?@[?@.a]]",
            "$[?@.a[?@.b[?@.c == 1]]]",
            "$[?!(!(@.a))]",
            "$..book[?@.price<10]..title");

    @Test
    @Requirement("4.2/query-construction")
    void parsedQueriesWriteThemselvesAsEquivalentText() {
        for (String text : CORPUS) {
            JsonPathQuery query = parse(text);
            assertEquals(query, parse(query.toString()), text);
        }
    }

    @Test
    @Requirement("4.2/query-construction")
    void namesFromVariablesAreEscaped() {
        String hostile = "a'] || $..*[?@ == '";
        JsonPathQuery query = new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Name(hostile)))));
        assertEquals("$['a\\'] || $..*[?@ == \\'']", query.toString());
        assertEquals(query, parse(query.toString()));
        Selector.Filter filter = new Selector.Filter(new ca.marcusdunn.jsonlens.path.core.query.LogicalExpression.Comparison(
                new Literal.StringLiteral("\u0000\n'"),
                ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator.EQUAL,
                new Literal.NullLiteral()));
        JsonPathQuery withFilter = new JsonPathQuery(List.of(new Segment.Child(List.of(
                filter, new Selector.Slice(Maybe.none(), Maybe.some(2L), Maybe.none())))));
        assertEquals("$[?'\\u0000\\n\\'' == null,:2]", withFilter.toString());
        assertEquals(withFilter, parse(withFilter.toString()));
    }

    @Test
    @Requirement("4.1/no-eval")
    void parserHasItsOwnGrammarAndNoScriptEngine() {
        assertTrue(ModuleDescriptors.runtimeModules(ModuleDescriptors.named("ca.marcusdunn.jsonlens.path.parser"))
                .stream().noneMatch(m -> m.equals("java.scripting") || m.startsWith("jdk.")));
        reject("$[?eval('1+1') == 2]", ParseError.UnknownFunction.class);
        reject("$[?(@.a + 1) == 2]");
        reject("$[?@.a.length == 2 || this.x]");
    }

    @Test
    @Requirement("4.1/parser-resources")
    void deepNestingGivesAnErrorValue() {
        int depth = 100_000;
        assertInstanceOf(ParseError.NestingTooDeep.class,
                reject("$[?" + "(".repeat(depth) + "@" + ")".repeat(depth) + "]"));
        assertInstanceOf(ParseError.NestingTooDeep.class,
                reject("$" + "[?@".repeat(depth) + "]".repeat(depth)));
        assertInstanceOf(ParseError.NestingTooDeep.class,
                reject("$[?" + "length(".repeat(depth) + "@" + ")".repeat(depth) + " == 1]"));
        assertInstanceOf(ParseError.NestingTooDeep.class,
                reject("$[?" + "!(".repeat(depth) + "@" + ")".repeat(depth) + "]"));
        int limit = JsonPathParser.MAX_NESTING_DEPTH - 1;
        parse("$[?" + "(".repeat(limit) + "@" + ")".repeat(limit) + "]");
    }

    @Test
    @Requirement("4.1/parser-resources")
    void longFlatQueriesParse() {
        assertEquals(100_000, parse("$" + "[0]".repeat(100_000)).segments().size());
        parse("$[?" + "@.a || ".repeat(100_000) + "@.b]");
        parse("$[" + "0,".repeat(100_000) + "0]");
    }

    @Test
    @Requirement("lib/parser-exception-free")
    void randomTextNeverThrows() {
        String alphabet = "$@.[]()'\"\\?*:,!=<>&|-+0123456789eEabcdlnrtuxyz_ \t\n\ré中😀\ud800\u0000";
        Random random = new Random(9535);
        for (int i = 0; i < 200_000; i++) {
            StringBuilder text = new StringBuilder(random.nextBoolean() ? "$" : "");
            int length = random.nextInt(24);
            for (int j = 0; j < length; j++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            STANDARD.parse(text.toString());
        }
    }

    @Test
    @Requirement("lib/parser-exception-free")
    void mutatedQueriesNeverThrow() {
        String replacements = "$@.[]()'\"\\?*:,!=<>&|-0e_ \u0000\ud800";
        Random random = new Random(42);
        for (String query : CORPUS) {
            for (int end = 0; end <= query.length(); end++) {
                STANDARD.parse(query.substring(0, end));
                STANDARD.parse(query.substring(end));
            }
            for (int i = 0; i < 5_000; i++) {
                StringBuilder mutated = new StringBuilder(query);
                int edits = 1 + random.nextInt(3);
                for (int e = 0; e < edits && !mutated.isEmpty(); e++) {
                    int at = random.nextInt(mutated.length());
                    switch (random.nextInt(3)) {
                        case 0 -> mutated.deleteCharAt(at);
                        case 1 -> mutated.insert(at, replacements.charAt(random.nextInt(replacements.length())));
                        default -> mutated.setCharAt(at, replacements.charAt(random.nextInt(replacements.length())));
                    }
                }
                STANDARD.parse(mutated.toString());
            }
        }
    }

    @Test
    @Requirement("lib/parser-exception-free")
    void randomBytesNeverThrow() {
        Random random = new Random(7);
        for (int i = 0; i < 50_000; i++) {
            byte[] bytes = new byte[random.nextInt(16)];
            random.nextBytes(bytes);
            if (bytes.length > 0 && random.nextBoolean()) {
                bytes[0] = '$';
            }
            STANDARD.parse(bytes);
        }
    }
}
