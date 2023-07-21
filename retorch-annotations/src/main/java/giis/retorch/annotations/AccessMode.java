package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * Access mode RETORCH custom annotation, has the following attributes :
 * resID--> String with the resource identification, by default None
 * sharing--> Boolean by default false
 * concurrency--> Integer by default -1
 * accessMode -->String by default NOASSIGNED
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
