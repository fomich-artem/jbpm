
package org.jbpm.process.builder.dialect.seamel;

import org.drools.lang.descr.ActionDescr;
import org.drools.rule.builder.PackageBuildContext;
import org.jbpm.process.builder.ActionBuilder;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.workflow.core.DroolsAction;

/**
 * Seam EL action builder
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELActionBuilder implements ActionBuilder {

	public void build(PackageBuildContext context, DroolsAction action, ActionDescr actionDescr, ContextResolver contextResolver) {
		action.setMetaData("Action", new SeamELAction(actionDescr.getText()));
	}

}
