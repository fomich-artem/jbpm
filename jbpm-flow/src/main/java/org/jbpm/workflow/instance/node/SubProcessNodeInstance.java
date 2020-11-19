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

package org.jbpm.workflow.instance.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.mvel.MVELSafeHelper;
import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.openicar.seamel.SeamELScriptEngine;
import org.jbpm.openicar.seamel.SeamELVariableBindings;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.impl.DataTransformerRegistry;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.StartProcessHelper;
import org.jbpm.process.instance.context.exception.ExceptionScopeInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.ContextInstanceFactory;
import org.jbpm.process.instance.impl.ContextInstanceFactoryRegistry;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.process.instance.impl.util.VariableUtil;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.core.node.DataAssociation;
import org.jbpm.workflow.core.node.SubProcessNode;
import org.jbpm.workflow.core.node.Transformation;
import org.jbpm.workflow.instance.impl.NodeInstanceResolverFactory;
import org.jbpm.workflow.instance.impl.VariableScopeResolverFactory;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.KieBase;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.Process;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.openicar.KnowledgeServiceLocator;
import org.kie.api.openicar.profiler.SimpleProfiler;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.DataTransformer;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.internal.KieInternalServices;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.process.CorrelationKeyFactory;
import org.kie.internal.runtime.manager.SessionNotFoundException;
import org.kie.internal.runtime.manager.context.CaseContext;
import org.kie.internal.runtime.manager.SessionNotFoundException;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime counterpart of a SubFlow node.
 * 
 */
public class SubProcessNodeInstance extends StateBasedNodeInstance implements EventListener, ContextInstanceContainer {

    private static final long serialVersionUID = 510l;
    private static final Logger logger = LoggerFactory.getLogger(SubProcessNodeInstance.class);
    
    // NOTE: ContetxInstances are not persisted as current functionality (exception scope) does not require it
    private Map<String, ContextInstance> contextInstances = new HashMap<String, ContextInstance>();
    private Map<String, List<ContextInstance>> subContextInstances = new HashMap<String, List<ContextInstance>>();

    private long processInstanceId;

    protected SubProcessNode getSubProcessNode() {
        return (SubProcessNode) getNode();
    }

    @Override
    public void internalTrigger(final NodeInstance from, String type) {
        String procId = getProcessInstance().getProcessId();

        SimpleProfiler.st(" all");
        SimpleProfiler.st(" all - " + procId);

        SimpleProfiler.st(" invoke super.internalTrigger - " + procId);
    	super.internalTrigger(from, type);
        SimpleProfiler.en(" invoke super.internalTrigger - " + procId);

    	// if node instance was cancelled, abort
		if (getNodeInstanceContainer().getNodeInstance(getId()) == null) {
			return;
		}
        if (!org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE.equals(type)) {
            throw new IllegalArgumentException(
                "A SubProcess node only accepts default incoming connections!");
        }

        SimpleProfiler.st(" assign input parameters - " + procId);

        SimpleScriptContext scriptContext = null;

        Map<String, Object> parameters = new HashMap<String, Object>();
        for (Iterator<DataAssociation> iterator =  getSubProcessNode().getInAssociations().iterator(); iterator.hasNext(); ) {
        	DataAssociation mapping = iterator.next();
        	Object parameterValue = null;
        	if (mapping.getTransformation() != null) {
            	Transformation transformation = mapping.getTransformation();
            	DataTransformer transformer = DataTransformerRegistry.get().find(transformation.getLanguage());
            	if (transformer != null) {
            		parameterValue = transformer.transform(transformation.getCompiledExpression(), getSourceParameters(mapping));

            	}
            } else {

	            VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
	                resolveContextInstance(VariableScope.VARIABLE_SCOPE, mapping.getSources().get(0));
	            if (variableScopeInstance != null) {
	                parameterValue = variableScopeInstance.getVariable(mapping.getSources().get(0));
	            } else {
	                String expression = mapping.getSources().get(0);
	            	try {
                        if (expression.contains("#{")) {
                            // evaluate expression thru Seam EL
                            if (scriptContext == null) {
                                scriptContext = new SimpleScriptContext();
                                scriptContext.setBindings(new SeamELVariableBindings(new NodeInstanceResolverFactory(this)), ScriptContext.ENGINE_SCOPE);
                            }
                            parameterValue = SeamELScriptEngine.instance().eval(expression, scriptContext);
                        } else {
                            parameterValue = MVELSafeHelper.getEvaluator().eval(expression, new NodeInstanceResolverFactory(this));
                        }
	            	} catch (Throwable t) {
	            	    parameterValue = VariableUtil.resolveVariable(expression, this);
	                    if (parameterValue == null) {
	                        //parameters.put(mapping.getTarget(), parameterValue);
	                    //} else {
    	            	    logger.error("Could not find variable scope for variable {}", mapping.getSources().get(0));
    	            	    logger.error("when trying to execute SubProcess node {}", getSubProcessNode().getName());
    	            	    logger.error("Continuing without setting parameter.");
	                    }
	            	}
	            }
            }
            if (parameterValue != null) {
            	parameters.put(mapping.getTarget(),parameterValue);
            }
        }
        SimpleProfiler.en(" assign input parameters - " + procId);
        
        String processId = getSubProcessNode().getProcessId();
        if (processId == null) {
            // if process id is not given try with process name
            processId = getSubProcessNode().getProcessName();
        }

        String expression = processId;
        if (expression.contains("#{")) {
            // evaluate expression thru Seam EL
            if (scriptContext == null) {
                scriptContext = new SimpleScriptContext();
                scriptContext.setBindings(new SeamELVariableBindings(new NodeInstanceResolverFactory(this)), ScriptContext.ENGINE_SCOPE);
            }
            try {
                processId = (String) SeamELScriptEngine.instance().eval(expression, scriptContext);
            } catch (ScriptException e) {
                logger.error("Could not find variable scope for variable/expression {}", expression);
                logger.error("when trying to replace variable in processId for sub process {}", getNodeName());
                logger.error("Continuing without setting process id.");
            }
        }

        /*
        // resolve processId if necessary
        Map<String, String> replacements = new HashMap<String, String>();
		Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(processId);
        while (matcher.find()) {
        	String paramName = matcher.group(1);
        	if (replacements.get(paramName) == null) {
            	VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
                	resolveContextInstance(VariableScope.VARIABLE_SCOPE, paramName);
                if (variableScopeInstance != null) {
                    Object variableValue = variableScopeInstance.getVariable(paramName);
                	String variableValueString = variableValue == null ? "" : variableValue.toString();
	                replacements.put(paramName, variableValueString);
                } else {
                	try {
                		Object variableValue = MVELSafeHelper.getEvaluator().eval(paramName, new NodeInstanceResolverFactory(this));
	                	String variableValueString = variableValue == null ? "" : variableValue.toString();
	                	replacements.put(paramName, variableValueString);
                	} catch (Throwable t) {
                	    logger.error("Could not find variable scope for variable {}", paramName);
                	    logger.error("when trying to replace variable in processId for sub process {}", getNodeName());
                	    logger.error("Continuing without setting process id.");
                	}
                }
        	}
        }
        for (Map.Entry<String, String> replacement: replacements.entrySet()) {
        	processId = processId.replace("#{" + replacement.getKey() + "}", replacement.getValue());
        }
         */

        // get real process id
        SimpleProfiler.st(" getLastProcessDefinitionVersionId - " + procId);
        processId = KnowledgeServiceLocator.getInstance().getRealProcesId(processId);
        SimpleProfiler.en(" getLastProcessDefinitionVersionId - " + procId);

        SimpleProfiler.st(" get process definition - " + procId);
        ProcessInstanceImpl mainProcessInstance = (ProcessInstanceImpl) getProcessInstance();
        InternalKnowledgeRuntime knowledgeRuntime = mainProcessInstance.getKnowledgeRuntime();

        KieBase kbase = knowledgeRuntime.getKieBase();
        // start process instance
        Process process = kbase.getProcess(processId);

        if (process == null) {
            // try to find it by name
            String latestProcessId = StartProcessHelper.findLatestProcessByName(kbase, processId);
            if (latestProcessId != null) {
                processId = latestProcessId;
                process = kbase.getProcess(processId);

            }
        }

        SimpleProfiler.en(" get process definition - " + procId);
        if (process == null) {
            logger.error("Could not find process {}", processId);
            logger.error("Aborting process");
            mainProcessInstance.setState(ProcessInstance.STATE_ABORTED);
        	throw new RuntimeException("Could not find process " + processId);
        } else {
            SimpleProfiler.st(" prepare sub-process instance - " + procId);
            SimpleProfiler.st(" createProcessInstance - " + procId);
            KieRuntime kruntime = ((ProcessInstance) getProcessInstance()).getKnowledgeRuntime();
            RuntimeManager manager = (RuntimeManager) kruntime.getEnvironment().get(EnvironmentName.RUNTIME_MANAGER);
            if (manager != null) {
                org.kie.api.runtime.manager.Context<?> context = ProcessInstanceIdContext.get();
                
                String caseId = (String) kruntime.getEnvironment().get(EnvironmentName.CASE_ID);
                if (caseId != null) {
                    context = CaseContext.get(caseId);
                }
                
                RuntimeEngine runtime = manager.getRuntimeEngine(context);
                kruntime = (KieRuntime) runtime.getKieSession();
            }
            if (getSubProcessNode().getMetaData("MICollectionInput") != null) {
                // remove foreach input variable to avoid problems when running in variable strict mode
                parameters.remove(getSubProcessNode().getMetaData("MICollectionInput"));
            }

            ProcessInstance processInstance = null;
            if (((WorkflowProcessInstanceImpl)getProcessInstance()).getCorrelationKey() != null) {
                // in case there is correlation key on parent instance pass it along to child so it can be easily correlated 
                // since correlation key must be unique for active instances it appends processId and UUID
                List<String> businessKeys = new ArrayList<>();
                businessKeys.add(((WorkflowProcessInstanceImpl)getProcessInstance()).getCorrelationKey());
                businessKeys.add(processId);
                businessKeys.add(UUID.randomUUID().toString());
                CorrelationKeyFactory correlationKeyFactory = KieInternalServices.Factory.get().newCorrelationKeyFactory();
                CorrelationKey subProcessCorrelationKey = correlationKeyFactory.newCorrelationKey(businessKeys);
                processInstance = (ProcessInstance) ((CorrelationAwareProcessRuntime)kruntime).createProcessInstance(processId, subProcessCorrelationKey, parameters);
            } else {
                processInstance = ( ProcessInstance ) kruntime.createProcessInstance(processId, parameters);
            }
            SimpleProfiler.en(" createProcessInstance - " + procId);
	    	this.processInstanceId = processInstance.getId();
	    	((ProcessInstanceImpl) processInstance).setMetaData("ParentProcessInstanceId", getProcessInstance().getId());
	    	((ProcessInstanceImpl) processInstance).setMetaData("ParentNodeInstanceId", getUniqueId());
	    	((ProcessInstanceImpl) processInstance).setMetaData("ParentNodeId", getSubProcessNode().getUniqueId());
	    	((ProcessInstanceImpl) processInstance).setParentProcessInstanceId(getProcessInstance().getId());
	    	((ProcessInstanceImpl) processInstance).setSignalCompletion(getSubProcessNode().isWaitForCompletion());
            String mainProcessInstanceIdsPath = mainProcessInstance.getProcessInstanceIdsPath();
            String subProcessInstanceIdsPathPrefix = mainProcessInstanceIdsPath != null ? mainProcessInstanceIdsPath + ProcessInstanceImpl.PROCESS_INSTANCE_IDS_PATH_SEPARATOR : "";
            ((ProcessInstanceImpl) processInstance).setProcessInstanceIdsPath(subProcessInstanceIdsPathPrefix + processInstance.getId());
            SimpleProfiler.en(" prepare sub-process instance - " + procId);
            SimpleProfiler.st(" start sub-process instance - " + procId);
            try {
                kruntime.startProcessInstance(processInstance.getId());
            } catch (Exception e) {
                String faultName = e.getClass().getName();
                if (handleError(faultName, processInstance)) {
                    return;
                } else {
                    throw e;
                }
            } finally {
	            SimpleProfiler.en(" start sub-process instance - " + procId);
            }

	    	if (!getSubProcessNode().isWaitForCompletion()) {
	    		SimpleProfiler.st(" triggerCompleted no wait 1 - " + procId);
	    		triggerCompleted();
	    		SimpleProfiler.en(" triggerCompleted no wait 1 - " + procId);
	    	} else if (processInstance.getState() == ProcessInstance.STATE_COMPLETED
	    	        || processInstance.getState() == ProcessInstance.STATE_ABORTED) {
	    	    SimpleProfiler.st(" triggerCompleted no wait 2 - " + procId);
	    	    processInstanceCompleted(processInstance);
	    	    SimpleProfiler.en(" triggerCompleted no wait 2 - " + procId);
	    	} else {
                SimpleProfiler.st(" add process listener - " + procId);    
	    		addProcessListener();
                SimpleProfiler.en(" add process listener - " + procId);
	    	}
        }
        SimpleProfiler.en(" all");
        SimpleProfiler.en(" all - " + procId);
    }

    @Override
    public void cancel(CancelType cancelType) {
        super.cancel(cancelType);
        if (getSubProcessNode() == null || !getSubProcessNode().isIndependent()) {
        	ProcessInstance processInstance = null;
        	InternalKnowledgeRuntime kruntime = ((ProcessInstance) getProcessInstance()).getKnowledgeRuntime();
        	RuntimeManager manager = (RuntimeManager) kruntime.getEnvironment().get(EnvironmentName.RUNTIME_MANAGER);
        	if (manager != null) {
        	    try {
            	    org.kie.api.runtime.manager.Context<?> context = ProcessInstanceIdContext.get(processInstanceId);
                    
                    String caseId = (String) kruntime.getEnvironment().get(EnvironmentName.CASE_ID);
                    if (caseId != null) {
                        context = CaseContext.get(caseId);
                    }
                    
                    RuntimeEngine runtime = manager.getRuntimeEngine(context);

                    KieRuntime managedkruntime = (KieRuntime) runtime.getKieSession();
            		processInstance = (ProcessInstance) managedkruntime.getProcessInstance(processInstanceId);
        	    } catch (SessionNotFoundException e) {
        	        // in case no session is found for parent process let's skip signal for process instance completion
        	    }
        	} else {
        		processInstance = (ProcessInstance) kruntime.getProcessInstance(processInstanceId);
        	}

            if (processInstance != null) {
            	processInstance.setState(ProcessInstance.STATE_ABORTED);
            }
        }
    }

    public long getProcessInstanceId() {
    	return processInstanceId;
    }

    public void internalSetProcessInstanceId(long processInstanceId) {
    	this.processInstanceId = processInstanceId;
    }

    public void addEventListeners() {
        super.addEventListeners();
        addProcessListener();
    }

    private void addProcessListener() {
    	getProcessInstance().addEventListener("processInstanceCompleted:" + processInstanceId, this, true);
    }

    public void removeEventListeners() {
        super.removeEventListeners();
        getProcessInstance().removeEventListener("processInstanceCompleted:" + processInstanceId, this, true);
    }

    @Override
	public void signalEvent(String type, Object event) {
		if (("processInstanceCompleted:" + processInstanceId).equals(type)) {
			processInstanceCompleted((ProcessInstance) event);
		} else {
			super.signalEvent(type, event);
		}
	}

    @Override
    public String[] getEventTypes() {
    	return new String[] { "processInstanceCompleted:" + processInstanceId };
    }

    public void processInstanceCompleted(ProcessInstance processInstance) {
        removeEventListeners();
        handleOutMappings(processInstance);
        if (processInstance.getState() == ProcessInstance.STATE_ABORTED) {
            String faultName = processInstance.getOutcome()==null?"":processInstance.getOutcome();
            if (handleError(faultName, processInstance)) {
                return;
            }
        }
        // handle dynamic subprocess
        if (getNode() == null) {
            setMetaData("NodeType", "SubProcessNode");
        }
        // if there were no exception proceed normally
        triggerCompleted();

    }

    private boolean handleError(String faultName, ProcessInstance processInstance) {
        // handle exception as sub process failed with error code
        ExceptionScopeInstance exceptionScopeInstance = (ExceptionScopeInstance) resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, faultName);
        if (exceptionScopeInstance != null) {

            exceptionScopeInstance.handleException(faultName, processInstance.getFaultData());
            if (getSubProcessNode() != null && !getSubProcessNode().isIndependent() && getSubProcessNode().isAbortParent()) {
                cancel();
            }
            return true;
        } else if (getSubProcessNode() != null && !getSubProcessNode().isIndependent() && getSubProcessNode().isAbortParent()) {
            ((ProcessInstance) getProcessInstance()).setState(ProcessInstance.STATE_ABORTED, faultName);
            return true;
        }
        return false;
    }
    private void handleOutMappings(ProcessInstance processInstance) {
        VariableScopeInstance subProcessVariableScopeInstance = (VariableScopeInstance)
	        processInstance.getContextInstance(VariableScope.VARIABLE_SCOPE);

        SimpleScriptContext scriptContext = null;

        SubProcessNode subProcessNode = getSubProcessNode();
        if (subProcessNode != null) {
		    for (Iterator<org.jbpm.workflow.core.node.DataAssociation> iterator= subProcessNode.getOutAssociations().iterator(); iterator.hasNext(); ) {
		    	org.jbpm.workflow.core.node.DataAssociation mapping = iterator.next();
		    	if (mapping.getTransformation() != null) {
                	Transformation transformation = mapping.getTransformation();
                	DataTransformer transformer = DataTransformerRegistry.get().find(transformation.getLanguage());
                	if (transformer != null) {
                		Object parameterValue = transformer.transform(transformation.getCompiledExpression(), subProcessVariableScopeInstance.getVariables());
                		VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
                        resolveContextInstance(VariableScope.VARIABLE_SCOPE, mapping.getTarget());
                        if (variableScopeInstance != null && parameterValue != null) {

                            variableScopeInstance.setVariable(mapping.getTarget(), parameterValue);
                        } else {
                            logger.warn("Could not find variable scope for variable {}", mapping.getTarget());
                            logger.warn("Continuing without setting variable.");
                        }
                	}
                } else {
    	        	VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
    		            resolveContextInstance(VariableScope.VARIABLE_SCOPE, mapping.getTarget());
    	        	if (variableScopeInstance != null) {
    	                String expression = mapping.getSources().get(0);
                    	Object value = subProcessVariableScopeInstance.getVariable(expression);
    	        		if (value == null) {
    		        		try {
                            	if (expression.contains("#{")) {
    	                            // evaluate expression thru Seam EL
    	                            if (scriptContext == null) {
    	                                scriptContext = new SimpleScriptContext();
        	                            scriptContext.setBindings(new SeamELVariableBindings(new VariableScopeResolverFactory(subProcessVariableScopeInstance)), ScriptContext.ENGINE_SCOPE);
    	                            }
    	                            value = SeamELScriptEngine.instance().eval(expression, scriptContext);
                            	} else {
    	                            value = MVELSafeHelper.getEvaluator().eval(expression, new VariableScopeResolverFactory(subProcessVariableScopeInstance));
                            	}
    		            	} catch (Throwable t) {
    		            		// do nothing
    		            	}
    		        	}
    		            variableScopeInstance.setVariable(mapping.getTarget(), value);
    		        } else {
    		            logger.error("Could not find variable scope for variable {}", mapping.getTarget());
    		            logger.error("when trying to complete SubProcess node {}", getSubProcessNode().getName());
    		            logger.error("Continuing without setting variable.");
    		        }
                }
		    }
        } else {
            // handle dynamic sub processes without data output mapping            
            mapDynamicOutputData(subProcessVariableScopeInstance.getVariables());
        }
    }

    public String getNodeName() {
    	Node node = getNode();
    	if (node == null) {
    		return "[Dynamic] Sub Process";
    	}
    	return super.getNodeName();
    }


    @Override
    public List<ContextInstance> getContextInstances(String contextId) {
        return this.subContextInstances.get(contextId);
    }

    @Override
    public void addContextInstance(String contextId, ContextInstance contextInstance) {
        List<ContextInstance> list = this.subContextInstances.get(contextId);
        if (list == null) {
            list = new ArrayList<ContextInstance>();
            this.subContextInstances.put(contextId, list);
        }
        list.add(contextInstance);
    }

    @Override
    public void removeContextInstance(String contextId, ContextInstance contextInstance) {
        List<ContextInstance> list = this.subContextInstances.get(contextId);
        if (list != null) {
            list.remove(contextInstance);
        }
    }

    @Override
    public ContextInstance getContextInstance(String contextId, long id) {
        List<ContextInstance> contextInstances = subContextInstances.get(contextId);
        if (contextInstances != null) {
            for (ContextInstance contextInstance: contextInstances) {
                if (contextInstance.getContextId() == id) {
                    return contextInstance;
                }
            }
        }
        return null;
    }

    @Override
    public ContextInstance getContextInstance(Context context) {
        ContextInstanceFactory conf = ContextInstanceFactoryRegistry.INSTANCE.getContextInstanceFactory(context);
        if (conf == null) {
            throw new IllegalArgumentException("Illegal context type (registry not found): " + context.getClass());
        }
        ContextInstance contextInstance = (ContextInstance) conf.getContextInstance(context, this, (ProcessInstance) getProcessInstance());
        if (contextInstance == null) {
            throw new IllegalArgumentException("Illegal context type (instance not found): " + context.getClass());
        }
        return contextInstance;
    }

    @Override
    public ContextContainer getContextContainer() {
        return getSubProcessNode();
    }

    protected Map<String, Object> getSourceParameters(DataAssociation association) {
        SimpleScriptContext scriptContext = null;
        Map<String, Object> parameters = new HashMap<String, Object>();
    	for (String sourceParam : association.getSources()) {
	    	Object parameterValue = null;
	        VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
	        resolveContextInstance(VariableScope.VARIABLE_SCOPE, sourceParam);
	        if (variableScopeInstance != null) {
	            parameterValue = variableScopeInstance.getVariable(sourceParam);
	        } else {
                try {

	            if (sourceParam.contains("#{")) {
                    // evaluate expression thru Seam EL
                    if (scriptContext == null) {
                        scriptContext = new SimpleScriptContext();
                        scriptContext.setBindings(new SeamELVariableBindings(new NodeInstanceResolverFactory(this)), ScriptContext.ENGINE_SCOPE);
                    }
                    parameterValue = SeamELScriptEngine.instance().eval(sourceParam, scriptContext);
                } else {
                    parameterValue = MVELSafeHelper.getEvaluator().eval(sourceParam, new NodeInstanceResolverFactory(this));
                }

	            } catch (Throwable t) {
	                logger.warn("Could not find variable scope for variable {}", sourceParam);
	            }
	        }
	        if (parameterValue != null) {
	        	parameters.put(association.getTarget(), parameterValue);
	        }
    	}

    	return parameters;
    }

}
