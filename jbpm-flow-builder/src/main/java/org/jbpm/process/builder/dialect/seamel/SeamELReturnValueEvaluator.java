
package org.jbpm.process.builder.dialect.seamel;

import java.io.Serializable;

import org.drools.runtime.process.ProcessContext;
import org.jbpm.openicar.seamel.SeamELUtils;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;

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
