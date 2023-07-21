package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * The RETORCH Resource custom annotation includes the following attributes:
 * resID: A String attribute with a default value of "None."
 * rType: A String attribute with a default value of "None."
 * parent: An array of Strings of, with a default containing only one item set to "None."
 * replaceable: An array of Strings, with a default containing only one item set to "None."
 * elasModel: An Elasticity Model attribute, created empty by default.
 * type: An Enum representing the different types of resources: PHYSICAL, LOGICAL, or COMPUTATIONAL.
 */
@Repeatable(Resources.class)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Resource {

    String resID() default "None";

    String rType() default "None";

    String[] parent() default {"None"};

    String[] replaceable() default {"None"};

    ElasticityModel elasModel() default @ElasticityModel();

    enum type {PHYSICAL, LOGICAL, COMPUTATIONAL}


}
