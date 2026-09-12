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

package org.jbpm.process.instance.context.variable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.drools.core.ClassObjectFilter;
import org.drools.core.event.ProcessEventSupport;
// mosaek (де-Сим 2026-09-12): Seam Contexts/Lifecycle/Log убраны — вне контейнера
// Seam Lifecycle.beginCall() падает «outside an initialized application»;
// slf4j вместо org.jboss.seam.log.Log
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.context.variable.VariableViolationException;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.context.AbstractContextInstance;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.instance.node.CompositeContextNodeInstance;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.CaseData;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.openicar.KnowledgeServiceLocator;
import org.kie.api.openicar.variable.VariableService;
import org.kie.api.openicar.variable.VariableValueWrapper;

/**
 * 
 */
public class VariableScopeInstance extends AbstractContextInstance {

    private static final long serialVersionUID = 511l;

    private transient Logger log = LoggerFactory.getLogger(VariableScopeInstance.class);

    /* Почему-то jbpm хранит кэш переменных, несмотря на директиву transient.
     * Возможно jBPM вообще никогда не делает десериализацию VariableScopeInstance,
     * а как-то иначе хранит переменные.
     * private transient Map<String, Object> variables = new HashMap<String, Object>();
     */

    private Map<String, VariableValueWrapper> persistentVariables = new HashMap<String, VariableValueWrapper>();

    private transient String variableIdPrefix = null;
    private transient String variableInstanceIdPrefix = null;

    @Override
    public String getContextType() {
        return VariableScope.VARIABLE_SCOPE;
    }

    public Object getVariable(String name) {
        
        Object value;/* = variables.get(name);
        if (value != null) {
            return value;
        }
         */
        VariableValueWrapper processVariable = internalGetPersistentVariables().get(name);
        if (processVariable != null)
            return getProcessVariableValue(name, processVariable);
         

        // support for processInstanceId and parentProcessInstanceId
        if ("processInstanceId".equals(name) && getProcessInstance() != null) {
            return getProcessInstance().getId();
        } else if ("parentProcessInstanceId".equals(name) && getProcessInstance() != null) {
            return getProcessInstance().getParentProcessInstanceId();
        }
        

        if (getProcessInstance() != null && getProcessInstance().getKnowledgeRuntime() != null) {
            // support for globals
            value = getProcessInstance().getKnowledgeRuntime().getGlobal(name);
            if (value != null) {
                return value;
            }
            // support for case file data
            @SuppressWarnings("unchecked")
            Collection<CaseData> caseFiles = (Collection<CaseData>) getProcessInstance().getKnowledgeRuntime().getObjects(new ClassObjectFilter(CaseData.class));
            if (caseFiles.size() == 1) {
                CaseData caseFile = caseFiles.iterator().next();
                // check if there is case file prefix and if so remove it before checking case file data
                final String lookUpName = name.startsWith(VariableScope.CASE_FILE_PREFIX) ? name.replaceFirst(VariableScope.CASE_FILE_PREFIX, "") : name;
                if (caseFile != null) {
                    return caseFile.getData(lookUpName);
                }
            }
            
        }    

        return null;
    }

    protected Object getProcessVariableValue(String name, VariableValueWrapper processVariable) {
        Object result = null;
        if (processVariable != null)
            try {
                result = processVariable.getValue();
            } catch (Exception e) {
                log.error("failed getVariable(...) invocation..., variable name: #0, ProcessVariable: #1", e, name, processVariable);
                throw e;
            }
        log.debug("getProcessVariableValue... read variable: name = #0, value = #1", name, result);
        return result;
    }

    protected Map<String, Object> internalGetVariables() {
        @SuppressWarnings("serial")
        Map<String, Object> mutableMap = new HashMap<String, Object>(internalGetPersistentVariables()) {
            int restoredCnt = 0;
            @Override
            public Object get(Object key) {
                Object result = super.get(key);
                if (result instanceof VariableValueWrapper) {
                    result = getProcessVariableValue(String.valueOf(key), ((VariableValueWrapper) result));
                    log.debug("getVariables() - lazy restoring variable: name = #0, value = #1", key, result);
                    put((String)key, result);
                    restoredCnt++;
                }
                return result;
            }
            protected void restoreAllVariables() {
                int size = size();
                if (size == 0 || size <= restoredCnt) return;
                log.debug("getVariables() - lazy restoring all variable values...");
                // mosaek (де-Сим): Seam-лайфцикл не нужен
                try {
                    for (Map.Entry<String, Object> entry : super.entrySet()) {
                        if (entry.getValue() instanceof VariableValueWrapper) {    
                            Object restoredValue = getProcessVariableValue(entry.getKey(), ((VariableValueWrapper)entry.getValue()));
                            log.debug("\t - read variable: name = #0, value = #1", entry.getKey(), restoredValue);
                            entry.setValue(restoredValue);
                            restoredCnt++;
                        }
                    }
                } finally {
                    // mosaek (де-Сим): см. выше
                }
            }
            @Override
            public boolean containsValue(Object value) {
                restoreAllVariables();
                return super.containsValue(value);
            }
            @Override
            public Object clone() {
                restoreAllVariables();
                return super.clone();
            }
            @Override
            public Collection<Object> values() {
                restoreAllVariables();
                return super.values();
            }
            @Override
            public Set<Entry<String, Object>> entrySet() {
                restoreAllVariables();
                return super.entrySet();
            }
        };
        return mutableMap;
    }

    protected Map<String, VariableValueWrapper> internalGetPersistentVariables() {
        if (persistentVariables == null) {
            persistentVariables = new HashMap<String, VariableValueWrapper>();
        }
        return persistentVariables;
    }

    public Map<String, VariableValueWrapper> getPersistentVariables() {
        return internalGetPersistentVariables();
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(internalGetVariables());
    }

    public void setVariable(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("The name of a variable may not be null!");
        }
        VariableService variableService = KnowledgeServiceLocator.getInstance(VariableService.class);
        VariableValueWrapper oldValueProcessVariable = internalGetPersistentVariables().get(name);
        VariableValueWrapper newValueProcessVariable = null;
        if (value != null) {
            newValueProcessVariable = variableService.wrapVariable(value);
        }
        if (oldValueProcessVariable == null || oldValueProcessVariable.isNull() || oldValueProcessVariable.equals(newValueProcessVariable)) {
            if (value == null) {
                return;
            }
        }
        /*Object oldValue = getVariable(name);
        if (oldValue == null) {
        	if (value == null) {
        		return;
        	}
        }*/
        // check if variable that is being set is readonly and has already been set
        if (oldValueProcessVariable != null && !oldValueProcessVariable.isNull() && !oldValueProcessVariable.equals(newValueProcessVariable) && getVariableScope().isReadOnly(name)) {
            throw new VariableViolationException(getProcessInstance().getId(), name, "Variable '" + name + "' is already set and is marked as read only");
        }
        
        ProcessEventSupport processEventSupport = ((InternalProcessRuntime) getProcessInstance()
    		.getKnowledgeRuntime().getProcessRuntime()).getProcessEventSupport();
    	processEventSupport.fireBeforeVariableChanged(
			(variableIdPrefix == null ? "" : variableIdPrefix + ":") + name,
			(variableInstanceIdPrefix == null? "" : variableInstanceIdPrefix + ":") + name,
			oldValueProcessVariable, newValueProcessVariable, getVariableScope().tags(name), getProcessInstance(),
			getProcessInstance().getKnowledgeRuntime());
        internalSetVariable(name, newValueProcessVariable);
        processEventSupport.fireAfterVariableChanged(
			(variableIdPrefix == null ? "" : variableIdPrefix + ":") + name,
			(variableInstanceIdPrefix == null? "" : variableInstanceIdPrefix + ":") + name,
    		oldValueProcessVariable, newValueProcessVariable, getVariableScope().tags(name), getProcessInstance(),
			getProcessInstance().getKnowledgeRuntime());
    }

    public void internalSetVariable(String name, Object value) {
        if (name.startsWith(VariableScope.CASE_FILE_PREFIX)) {
            String nameInCaseFile = name.replaceFirst(VariableScope.CASE_FILE_PREFIX, "");            
            // store it under case file rather regular variables
            @SuppressWarnings("unchecked")
            Collection<CaseData> caseFiles = (Collection<CaseData>) getProcessInstance().getKnowledgeRuntime().getObjects(new ClassObjectFilter(CaseData.class));
            if (caseFiles.size() == 1) {
                CaseData caseFile = (CaseData) caseFiles.iterator().next();
                FactHandle factHandle = getProcessInstance().getKnowledgeRuntime().getFactHandle(caseFile);
                
                if (value == null) {
                    caseFile.remove(nameInCaseFile);
                } else {
                    caseFile.add(nameInCaseFile, value);
                }
                // case data fire rules only if the state is not pending (active)
                if (getProcessInstance().getState() != ProcessInstance.STATE_PENDING) {
                    getProcessInstance().getKnowledgeRuntime().update(factHandle, caseFile);
                    ((KieSession) getProcessInstance().getKnowledgeRuntime()).fireAllRules();
                }
                return;
            }
            
        }
        // not a case, store it in normal variables
        log.debug("internalSetVariable... name = #0, value = #1", name, value);

        VariableValueWrapper oldVariable = null;
        if (value != null && (!(value instanceof VariableValueWrapper) || !((VariableValueWrapper)value).isNull())) {
            oldVariable = internalGetPersistentVariables().get(name);
            if (oldVariable != null) {
                oldVariable.setValue(value);
            } else if (value instanceof VariableValueWrapper) {
                internalGetPersistentVariables().put(name, (VariableValueWrapper) value);
            } else {
            	VariableService variableService = KnowledgeServiceLocator.getInstance(VariableService.class);
            	VariableValueWrapper newProcessVariable = variableService.wrapVariable(value);
                internalGetPersistentVariables().put(name, newProcessVariable);
            }
        } else {
            internalGetPersistentVariables().put(name, null);
        }
    }

    public VariableScope getVariableScope() {
        return (VariableScope) getContext();
    }

    @Override
    public void setContextInstanceContainer(ContextInstanceContainer contextInstanceContainer) {
        super.setContextInstanceContainer(contextInstanceContainer);
        for (Variable variable : getVariableScope().getVariables()) {
            if (variable.getValue() != null) {
                setVariable(variable.getName(), variable.getValue());
            }
        }
        if (contextInstanceContainer instanceof CompositeContextNodeInstance) {
            this.variableIdPrefix = ((Node) ((CompositeContextNodeInstance) contextInstanceContainer).getNode()).getUniqueId();
            this.variableInstanceIdPrefix = ((CompositeContextNodeInstance) contextInstanceContainer).getUniqueId();
        }
    }
    
    public void enforceRequiredVariables() {
        Map<String, VariableValueWrapper> variables = getPersistentVariables();
        VariableScope variableScope = getVariableScope();
        for (Variable variable : variableScope.getVariables()) {
            String name = variable.getName();
            if (variableScope.isRequired(name)) {  
                // check case file if it is prefixed
                if (name.startsWith(VariableScope.CASE_FILE_PREFIX)) {
                    if (!findCaseData(name)) {
                        throw new VariableViolationException(getProcessInstance().getId(), name, "Case file item '" + name + "' is required but not set");
                        
                    }
                    // otherwise check variables                    
                } else if (!hasData(variables.get(name))) {
                    throw new VariableViolationException(getProcessInstance().getId(), name, "Variable '" + name + "' is required but not set");
                }
                
            }
        }
    }
    
    protected boolean findCaseData(String name) {
        boolean found = false;
        String nameInCaseFile = name.replaceFirst(VariableScope.CASE_FILE_PREFIX, "");            
        // store it under case file rather regular variables
        @SuppressWarnings("unchecked")
        Collection<CaseData> caseFiles = (Collection<CaseData>) getProcessInstance().getKnowledgeRuntime().getObjects(new ClassObjectFilter(CaseData.class));
        if (caseFiles.size() == 1) {
            CaseData caseData = caseFiles.iterator().next();
            if (hasData(caseData.getData(nameInCaseFile))) {
                found = true;
            }
        }
        
        return found;
    }
    
    private boolean hasData(Object data) {
        return data != null && (!(data instanceof CharSequence) || !data.toString().trim().isEmpty());
    }

}
