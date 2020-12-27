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

package org.jbpm.openicar.seamel;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.impl.NodeInstanceResolverFactory;
import org.kie.api.runtime.process.ProcessContext;

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
