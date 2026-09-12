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

/**
 * Timer storage API
 *
 * mosaek (де-Сим, 2026-09-12): канон резолвил стор через Seam
 * ({@code org.jboss.seam.Component.getInstance( TIMER_STORE_SERVICE_CONTEXT_VARIABLE, true )});
 * вне контейнера Seam (Spring Boot) реестр недоступен, поэтому стор
 * выставляется бином явно при старте (JPATimerStoreService &rarr;
 * {@link #setInstance(TimerStoreService)}). По образцу
 * org.kie.api.openicar.KnowledgeServiceLocator. Поле
 * TIMER_STORE_SERVICE_CONTEXT_VARIABLE оставлено для совместимости,
 * в резолве не участвует.
 *
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a> <br>
 *
 */
public class TimerStoreServiceLocator {

	public static String TIMER_STORE_SERVICE_CONTEXT_VARIABLE = "timerStoreService";

	private static volatile TimerStoreService instance;

	public static void setInstance(TimerStoreService storeService) {
		TimerStoreServiceLocator.instance = storeService;
	}

	public static TimerStoreService getInstance() {
		return instance;
	}

}
