package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
RETORCH annotation represents whether the resource can be instantiated multiple times and the cost incurred for each new instantiation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ElasticityModel {
    /**
     * String with a unique value that identifies the elasticity model. Its default value is 'None'
     */
    String elasID() default "None";
    /**
     * Integer that represents the maximum number of instances that can be deployed with this elasticity model. Its default value is -1.
     */
    int elasticity() default -1;
    /**
     * Double that represents the cost incurred when a new instantiation is performed. Its default value is -1.
     */
    double elasCost() default -1;
}