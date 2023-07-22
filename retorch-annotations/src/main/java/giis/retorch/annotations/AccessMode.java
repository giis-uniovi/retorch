package giis.retorch.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * RETORCH custom annotation specify for each pair of test case and resource that determines if the operations performed
 * during the test execution modify the resource or not, and how. This annotation can be retrieved in execution time and processed
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(AccessModes.class)
@Documented
public @interface AccessMode {
    /**
     * String with a unique value that identifies the resource associated with this access mode. Its default value is 'None'
     */
    String resID() default "None";
    /**
     * Boolean indicating whether the resource can be accessed concurrently by several test cases. Its default value is 'None'.
     */
    boolean sharing() default false;
    /**
     * Integer that sets the maximum concurrency supported by the resource when sharing is enabled (True). Its default value is -1.
     */
    int concurrency() default -1;
    /**
     * String that specifies the type of access mode performed on the resource, e.g., 'READ-ONLY', 'READ-WRITE', 'WRITE-ONLY', 'DYNAMIC' or 'NO-ACCESS'. Its default value is 'NOASSIGNED'.
     */
    String accessMode() default "NOASSIGNED";
}