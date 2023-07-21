package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//Copyright 2020, Cristian Augusto, All rights reserved.

/**
 * RETORCH Resource custom annotation has the following attributes
 * resID --> String by default "None"
 * rType--> String by default "None"
 * parent--> Array of Strings, by default only one item with the "None"
 * replaceable--> Array of Strings, by default only  one item with the "None" Value
 * elasModel --> Elasticity Model, by default is created empty
 * type --> Enum with the different types of resource: PHYSICAL, LOGICAL or COMPUTATIONAL
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
