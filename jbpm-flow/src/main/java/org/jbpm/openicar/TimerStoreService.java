
package org.jbpm.openicar;

import org.drools.core.marshalling.impl.MarshallerReaderContext;
import org.jbpm.process.instance.timer.TimerInstance;
import org.jbpm.process.instance.timer.TimerManager;
import org.kie.api.runtime.Environment;

/**
 * Timer storage API
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a> <br>
 *
 */
public interface TimerStoreService {

	Long generateTimerId(TimerInstance timer);

	TimerInstance getTimer(Long timerId);

	void internalAddTimer(TimerInstance timerInstance);

	void persistTimer(TimerInstance timer, Environment env);

	void removeTimer(TimerInstance timer);

	void loadTimers();

	void convertTimer(TimerManager timerManager, MarshallerReaderContext inCtx, TimerInstance timer, long processInstanceId);

}
