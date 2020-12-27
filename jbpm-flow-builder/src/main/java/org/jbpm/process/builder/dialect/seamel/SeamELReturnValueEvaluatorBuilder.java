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

import org.drools.compiler.compiler.DescrBuildError;
import org.drools.compiler.compiler.ReturnValueDescr;
import org.drools.compiler.rule.builder.PackageBuildContext;
import org.jbpm.process.builder.ReturnValueEvaluatorBuilder;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.instance.impl.ReturnValueConstraintEvaluator;

/**
 * Seam EL return value evaluator builder
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELReturnValueEvaluatorBuilder implements ReturnValueEvaluatorBuilder {

	public void build(PackageBuildContext context,
			ReturnValueConstraintEvaluator returnValueConstraintEvaluator,
			ReturnValueDescr returnValueDescr, ContextResolver contextResolver) {

		String text = returnValueDescr.getText();
		try {
			SeamELReturnValueEvaluator evaluator = new SeamELReturnValueEvaluator(text);
			returnValueConstraintEvaluator.setEvaluator(evaluator);
		} catch ( final Exception e ) {
			context.getErrors().add( new DescrBuildError(
					context.getParentDescr(),
					returnValueDescr,
					null,
					"Unable to build expression for 'constraint' " + returnValueDescr.getText() + "': " + e ) );
		}

	}

}
