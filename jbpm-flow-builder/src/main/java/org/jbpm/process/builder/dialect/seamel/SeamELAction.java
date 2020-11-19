
package org.jbpm.process.builder.dialect.seamel;

import java.io.Serializable;

import org.drools.runtime.process.ProcessContext;
import org.jbpm.openicar.seamel.SeamELUtils;
import org.jbpm.process.instance.impl.Action;

/**
 * Seam EL action
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELAction implements Action, Serializable {

	private static final long serialVersionUID = 1L;

	protected String expression;

	/**
	 * @param expression
	 */
	public SeamELAction(String expression) {
		super();
		this.expression = expression;
	}

	public void execute(ProcessContext processContext) throws Exception {
		SeamELUtils.evaluate(processContext, expression);
	}

}
