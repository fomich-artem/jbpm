
package org.jbpm.openicar.seamel;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.drools.runtime.process.ProcessContext;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.impl.NodeInstanceResolverFactory;

/**
 * Seam EL utilities
 * 
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELUtils {

	public static Object evaluate(ProcessContext processContext, String expression) throws ScriptException {
		SimpleScriptContext scriptContext = new SimpleScriptContext();
		NodeInstanceResolverFactory variableResolverFactory = new NodeInstanceResolverFactory((NodeInstance)processContext.getNodeInstance());
		SeamELVariableBindings bindings = new SeamELVariableBindings(variableResolverFactory);
		bindings.put("kcontext", processContext);
		scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		return SeamELScriptEngine.instance().eval(expression, scriptContext);
	}

}
