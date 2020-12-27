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

import org.jbpm.process.builder.ActionBuilder;
import org.jbpm.process.builder.AssignmentBuilder;
import org.jbpm.process.builder.ProcessBuildContext;
import org.jbpm.process.builder.ProcessClassBuilder;
import org.jbpm.process.builder.ReturnValueEvaluatorBuilder;
import org.jbpm.process.builder.dialect.ProcessDialect;

/**
 * Seam EL process dialect
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELProcessDialect implements ProcessDialect {

	public static final String ID = "seamel";
	public static final String SEAMEL_LANGUAGE = "http://comsoft.org/seamel";

	private static final SeamELActionBuilder SEAMEL_ACTION_BUILDER = new SeamELActionBuilder();
	private static final SeamELReturnValueEvaluatorBuilder SEAMEL_RETURN_VALUE_EVALUATOR_BUILDER = new SeamELReturnValueEvaluatorBuilder();

	public ActionBuilder getActionBuilder() {
		return SEAMEL_ACTION_BUILDER;
	}

	public ReturnValueEvaluatorBuilder getReturnValueEvaluatorBuilder() {
		return SEAMEL_RETURN_VALUE_EVALUATOR_BUILDER;
	}

	public ProcessClassBuilder getProcessClassBuilder() {
		throw new UnsupportedOperationException("SeamELProcessDialect.getProcessClassBuilder is not supported");
	}

	public AssignmentBuilder getAssignmentBuilder() {
		throw new UnsupportedOperationException("SeamELProcessDialect.getAssignmentBuilder is not supported");
	}

	public void addProcess(ProcessBuildContext context) {
		// TODO Auto-generated method stub
	}

}
