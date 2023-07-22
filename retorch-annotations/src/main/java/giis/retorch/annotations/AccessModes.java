package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is used to tag multiple Access Modes onto a test case.
 * It has the RUNTIME retention policy, allowing retrieval of its information during the execution of the application.
 * Additionally, this annotation is automatically documented in the Javadoc.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AccessModes {
    AccessMode[] value();

}
