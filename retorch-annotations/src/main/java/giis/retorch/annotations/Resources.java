/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * RETORCH annotation is used to enable support for multiple Resource.class annotations within the same test case.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Resources {
    Resource[] value();
}