package com.willmeyer.jrs232;

import org.junit.*;

public class Tests 
{
	DummyDevice dev = null;
	
	@Before
    public void beforeTest() throws Exception {
		dev = new DummyDevice("COM1");
		dev.enumPorts();
		dev.connect();
    }

	@After
    public void afterTest() throws Exception {
		dev.disconnect();
    }

	@Test
	public void testSomething() throws Exception 
    {
    }

}
