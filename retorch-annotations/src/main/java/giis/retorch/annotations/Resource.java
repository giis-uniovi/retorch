package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
RETORCH custom annotation specifies a physical, logical, or computational entity required during the test execution,
 along with its attributes. This annotation can be retrieved and processed during runtime
 */
@Repeatable(Resources.class)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Resource {
    /**
     * String with a unique value that identifies the resource. Its default value is 'None'
     */
    String resID() default "None";
    /**
     * String that specifies the resource type: type.LOGICAL, type.PHYSICAL, or type.COMPUTATIONAL. Its default value is 'None'.
     */
    String rType() default "None";
    /**
     *  Array of strings containing the different resID values of the hierarchical parents of the resource. Its default value is {'None'}.
     */
    String[] parent() default {"None"};
    /**
     *  Array of strings containing the different resID values of the resources that may be interchangeable by a new
     *  instance or another equivalent resource, with no penalty for a given test case. Its default value is {'None'}.  */
    String[] replaceable() default {"None"};
    /**
     *  ElasticityModel associated with the resource
     */
    ElasticityModel elasModel() default @ElasticityModel();
    /**
     *  Enumeration with the different resource Types (rType)
     */
    enum type {PHYSICAL, LOGICAL, COMPUTATIONAL}
}