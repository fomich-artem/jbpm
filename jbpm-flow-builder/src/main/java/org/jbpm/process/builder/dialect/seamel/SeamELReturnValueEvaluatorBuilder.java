
package org.jbpm.process.builder.dialect.seamel;

import org.drools.compiler.DescrBuildError;
import org.drools.compiler.ReturnValueDescr;
import org.drools.rule.builder.PackageBuildContext;
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
