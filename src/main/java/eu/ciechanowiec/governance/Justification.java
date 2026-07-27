package eu.ciechanowiec.governance;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States why an accompanying {@link SuppressWarnings} is correct. A suppression records that a rule was
 * consciously set aside. Without the reason, a later reader cannot tell a considered exception from a
 * silenced defect, and the only safe move is to leave it alone forever. Pairing the two keeps the
 * reason in the code rather than in somebody's memory, and the {@code SuppressionNeedsJustification}
 * rule in {@code pmd.xml} fails the build when it is missing.
 *
 * <p>The retention is {@link RetentionPolicy#SOURCE}: the justification is addressed to whoever reads
 * or changes the code, so it has no business in the bytecode, and the type itself is filtered out of
 * the executable jar.
 *
 * <p>This type deliberately sits outside the project's own package so the governance harness can be
 * lifted into another project without carrying that project's name with it.
 */
@Documented
@Retention(
    RetentionPolicy.SOURCE
)
@Target(
    {
        ElementType.TYPE, ElementType.METHOD, ElementType.FIELD,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER
    }
)
public @interface Justification {

    /**
     * Why the accompanying suppression is correct.
     *
     * @return the reason the suppressed rule does not apply here
     */
    @Justification(
        "an annotation attribute has no caller by design: this one is read by whoever changes the code"
    )
    @SuppressWarnings(
        "UnusedReturnValue"
    )
    String value();
}
