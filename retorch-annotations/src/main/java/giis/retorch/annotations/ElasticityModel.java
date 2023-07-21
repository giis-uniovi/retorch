package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * The RETORCH Elasticity Model custom annotation includes the following attributes:
 * elasID: A String attribute with a default value of "None."
 * elasticity: An Integer attribute with a default value of -1.
 * elasCost: A double attribute with a default value of -1.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ElasticityModel {
    String elasID() default "None";

    int elasticity() default -1;

    double elasCost() default -1;


}
