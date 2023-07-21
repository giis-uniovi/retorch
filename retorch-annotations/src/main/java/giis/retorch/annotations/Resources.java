package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * Annotation required to tag over a test several resources.
 * This annotation has also the RUNTIME retention policy to allow retrieve the information of it
 * during the execution of the application
 * This annotation is documented automatically in the Javadoc
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Resources {

    Resource[] value();
}
