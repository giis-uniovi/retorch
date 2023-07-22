package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is used to tag multiple resources over a test.
 * It has the RUNTIME retention policy, which allows retrieving the information during the execution of the application.
 * Additionally, this annotation is automatically documented in the Javadoc.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Resources {

    Resource[] value();
}
