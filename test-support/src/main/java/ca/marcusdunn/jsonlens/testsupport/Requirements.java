package ca.marcusdunn.jsonlens.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Holds more than one {@link Requirement} on the same element. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Requirements {

    /**
     * Returns the requirements.
     *
     * @return the requirements
     */
    Requirement[] value();
}
