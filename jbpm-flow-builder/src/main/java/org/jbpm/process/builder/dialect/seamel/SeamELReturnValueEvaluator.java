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

package org.jbpm.process.builder.dialect.seamel;

import java.io.Serializable;

import org.jbpm.openicar.seamel.SeamELUtils;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.kie.api.runtime.process.ProcessContext;

/**
 * Seam EL return value evaluator
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELReturnValueEvaluator implements ReturnValueEvaluator, Serializable {

	private static final long serialVersionUID = 1L;

	private String expression;

	/**
	 * @param expression
	 */
	public SeamELReturnValueEvaluator(String expression) {
		this.expression = expression;
	}

	public Object evaluate(ProcessContext processContext) throws Exception {
		return SeamELUtils.evaluate(processContext, expression);
	}

}
