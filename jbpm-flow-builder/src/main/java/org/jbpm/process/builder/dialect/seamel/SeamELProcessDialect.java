
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
