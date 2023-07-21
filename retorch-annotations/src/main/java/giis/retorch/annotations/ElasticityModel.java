package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * RETORCH Elasticity Model custom annotation has the following attributes
 * elasID --> String by default "None"
 * elasticity--> Integer by default -1
 * elasCost--> double by default -1
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ElasticityModel {
    String elasID() default "None";

    int elasticity() default -1;

    double elasCost() default -1;


}
