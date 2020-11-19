/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

package org.jbpm.workflow.instance.impl;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

import org.drools.core.spi.ProcessContext;
import org.drools.mvel.MVELSafeHelper;
import org.jbpm.openicar.seamel.SeamELScriptEngine;
import org.jbpm.openicar.seamel.SeamELVariableBindings;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.DataType;
import org.jbpm.process.core.impl.DataTransformerRegistry;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.impl.ExtendedNodeImpl;
import org.jbpm.workflow.core.node.Assignment;
import org.jbpm.workflow.core.node.DataAssociation;
import org.jbpm.workflow.core.node.Transformation;
import org.kie.api.openicar.profiler.SimpleProfiler;
import org.kie.api.runtime.process.DataTransformer;
import org.kie.api.runtime.process.NodeInstance;

public abstract class ExtendedNodeInstanceImpl extends NodeInstanceImpl {

	private static final long serialVersionUID = 510l;
	
	public ExtendedNodeImpl getExtendedNode() {
		return (ExtendedNodeImpl) getNode();
	}
	
	public void internalTrigger(NodeInstance from, String type) {
		triggerEvent(ExtendedNodeImpl.EVENT_NODE_ENTER);
	}
	
    public void triggerCompleted(boolean remove) {
        triggerCompleted(org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE, remove);
    }
    
	protected void triggerCompleted(String type, boolean remove) {
		triggerEvent(ExtendedNodeImpl.EVENT_NODE_EXIT);
		super.triggerCompleted(type, remove);
	}
	
	protected void triggerEvent(String type) {
		ExtendedNodeImpl extendedNode = getExtendedNode();
		if (extendedNode == null) {
			return;
		}
		List<DroolsAction> actions = extendedNode.getActions(type);
		if (actions != null) {
			for (DroolsAction droolsAction: actions) {
			    Action action = (Action) droolsAction.getMetaData("Action");
				executeAction(action);
			}
		}
	}
    protected void mapOutputSetVariables(NodeInstance nodeInstance, List<DataAssociation> dataOututAssoctiation, Map<String, Object> ouputData) {
        this.mapOutputSetVariables(nodeInstance, dataOututAssoctiation, ouputData, (target, value) -> {});
    }
    protected void mapOutputSetVariables(NodeInstance nodeInstance, List<DataAssociation> dataOututAssoctiation, Map<String, Object> ouputData, BiConsumer<String, Object> parameterSet) {
        SimpleProfiler.st(" - assign output parameters");
        SimpleScriptContext scriptContext = null;
        for (Iterator<DataAssociation> iterator = dataOututAssoctiation.iterator(); iterator.hasNext();) {
            DataAssociation association = iterator.next();
            if (association.getTransformation() != null) {
                Transformation transformation = association.getTransformation();
                DataTransformer transformer = DataTransformerRegistry.get().find(transformation.getLanguage());
                if (transformer != null) {
                    Object parameterValue = transformer.transform(transformation.getCompiledExpression(), ouputData);
                    VariableScopeInstance variableScopeInstance = (VariableScopeInstance) resolveContextInstance(VariableScope.VARIABLE_SCOPE, association.getTarget());
                    if (variableScopeInstance != null && parameterValue != null) {

                        variableScopeInstance.getVariableScope().validateVariable(getProcessInstance().getProcessName(), association.getTarget(), parameterValue);

                        variableScopeInstance.setVariable(association.getTarget(), parameterValue);
                    } else {
                        logger.warn("Could not find variable scope for variable {}", association.getTarget());
                        logger.warn("when trying to complete Work Item {}", nodeInstance.getNodeName());
                        logger.warn("Continuing without setting variable.");
                    }
                    if (parameterValue != null) {
                        parameterSet.accept(association.getTarget(), parameterValue);
                    }
                }
            } else if (association.getAssignments() == null || association.getAssignments().isEmpty()) {
                VariableScopeInstance variableScopeInstance = (VariableScopeInstance) resolveContextInstance(VariableScope.VARIABLE_SCOPE, association.getTarget());
                if (variableScopeInstance != null) {
                    String expression = association.getSources().get(0);
                    Object value = ouputData.get(expression);
                    if (value == null) {
                        try {
                            if (expression.contains("#{")) {
                                // evaluate expression thru Seam EL
                                if (scriptContext == null) {
                                    scriptContext = new SimpleScriptContext();
                                    scriptContext.setBindings(new SeamELVariableBindings(new MapResolverFactory(ouputData)), ScriptContext.ENGINE_SCOPE);
                                }
                                value = SeamELScriptEngine.instance().eval(expression, scriptContext);
                            } else {
                                try {
                                    value = MVELSafeHelper.getEvaluator().eval(expression, new MapResolverFactory(ouputData));
                                } catch (Throwable t) {
                                    // ничего не делаем, т.к. если значение переменной = null, то MVEL говорит, что нет такой функции/метода и т.п.
                                }
                            }
                            log.debug("resolved outgoing association source [#0] value = #1", expression, value);
                        } catch (Exception e1) {
                            SimpleProfiler.en(" - assign output parameters");
                            e1.printStackTrace();
                            if (e1 instanceof RuntimeException) throw (RuntimeException)e1;
                            throw new IllegalStateException(e1);
                        }
                    } else {
                        log.debug("variable [#0] value [#1] resolved from variable scope", expression, value);
                    }
                    Variable varDef = variableScopeInstance.getVariableScope().findVariable(association.getTarget());
                    DataType dataType = varDef.getType();
                    // exclude java.lang.Object as it is considered unknown type
                    if (!dataType.getStringType().endsWith("java.lang.Object") &&
                        !dataType.getStringType().endsWith("Object") && value instanceof String) {
                        value = dataType.readValue((String) value);
                    } else {
                        variableScopeInstance.getVariableScope().validateVariable(getProcessInstance().getProcessName(), association.getTarget(), value);
                    }
                    log.debug("set variable [#0] value [#1]", association.getTarget(), value);
                    variableScopeInstance.setVariable(association.getTarget(), value);
                } else {
                    /*
                    logger.warn("Could not find variable scope for variable {}", association.getTarget());
                    logger.warn("when trying to complete Work Item {}",nodeInstance.getNodeName());
                    logger.warn("Continuing without setting variable.");
                     */
                    SimpleProfiler.en(" - assign output parameters");
                    log.error("Could not find variable scope for variable [#0] when trying to complete Work Item [#1]", association.getTarget(), nodeInstance.getNodeName());
                    throw new IllegalStateException("Could not find variable scope for variable [" + association.getTarget() + "] when trying to complete Work Item [" + nodeInstance.getNodeName() + "]");
                }

            } else {
                try {
                    for (Iterator<Assignment> it = association.getAssignments().iterator(); it.hasNext();) {
                        handleAssignment(it.next());
                    }
                } catch (Exception e) {
                    SimpleProfiler.en(" - assign output parameters");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
        SimpleProfiler.en(" - assign output parameters");
    }
    
    protected void handleAssignment(Assignment assignment) {
        AssignmentAction action = (AssignmentAction) assignment.getMetaData("Action");
        try {
            ProcessContext context = new ProcessContext(getProcessInstance().getKnowledgeRuntime());
            context.setNodeInstance(this);
            action.execute(this, context);
        } catch (Exception e) {
            throw new RuntimeException("unable to execute Assignment", e);
        }
    }
	
}
