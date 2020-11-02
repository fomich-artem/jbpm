package org.jbpm.openicar;

import org.jboss.seam.Component;

/**
 * Timer storage API
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a> <br>
 *
 */
public class TimerStoreServiceLocator {

	public static String TIMER_STORE_SERVICE_CONTEXT_VARIABLE = "timerStoreService";

	public static TimerStoreService getInstance() {
		return (TimerStoreService) Component.getInstance(TIMER_STORE_SERVICE_CONTEXT_VARIABLE, true);
	}

}
