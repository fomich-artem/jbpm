/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
