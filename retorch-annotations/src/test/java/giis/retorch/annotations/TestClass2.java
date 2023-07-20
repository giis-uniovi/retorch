package giis.retorch.annotations;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClass2 {
	private final Logger log=LoggerFactory.getLogger(this.getClass());

	@Rule 
	public TestName testName = new TestName();
	
	@Before
	public void setUp() {
		log.info("****** Running test: {} ******", testName.getMethodName());
	}
	@Test
	public void testFunction21() {
		assertEquals("HELLO WORLD", new Class2().function21());
	}

}
