package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * RETORCH annotation is used to enable support for multiple AccessMode.class annotations within the same test case.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AccessModes {
    AccessMode[] value();
}