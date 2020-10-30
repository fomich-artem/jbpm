package org.jbpm;

import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;

public class SimpleProfiler {

	static Log log = Logging.getLog(SimpleProfiler.class);

	public static void st(String string) {
		log.warn("calling dump SimpleProfiler.st('#0')", string);
	}

	public static void en(String string) {
		log.warn("calling dump SimpleProfiler.en('#0')", string);	
	}

}
