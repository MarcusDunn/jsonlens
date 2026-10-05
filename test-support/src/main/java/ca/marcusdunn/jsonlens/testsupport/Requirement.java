package ca.marcusdunn.jsonlens.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a test to a row of the requirement catalog {@code spec/requirements.txt}.
 *
 * <p>The {@code verifyTraceability} task reads this annotation from the test source text.
 * Write the identifier as a string literal on one line, for example
 * {@code @Requirement("2.7/escaping")}. Put the annotation on a test method or a test class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Requirements.class)
public @interface Requirement {

    /**
     * Returns the identifier of the catalog row.
     *
     * @return the identifier of the catalog row
     */
    String value();
}
