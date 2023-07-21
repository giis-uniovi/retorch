package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * The RETORCH custom annotation for Access Mode has the following attributes:
 * resID: A String representing the resource identifier, with a default value of "None."
 * sharing: A Boolean indicating if sharing is enabled, with a default value of false.
 * concurrency: An Integer specifying the concurrency level, with a default value of -1.
 * accessMode: A String representing the access mode, with a default value of "NOASSIGNED"
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(AccessModes.class)
@Documented
public @interface AccessMode {
    String resID() default "None";

    boolean sharing() default false;

    int concurrency() default -1;

    String accessMode() default "NOASSIGNED";


}
