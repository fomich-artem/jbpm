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

package org.jbpm.persistence.processinstance;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;

import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.persistence.api.TransactionManager;
import org.drools.persistence.api.TransactionManagerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jbpm.persistence.JpaProcessPersistenceContextManager;
import org.jbpm.persistence.api.ProcessPersistenceContext;
import org.jbpm.persistence.api.ProcessPersistenceContextManager;
import org.jbpm.persistence.api.integration.EventManagerProvider;
import org.jbpm.persistence.api.integration.InstanceView;
import org.jbpm.persistence.api.integration.model.CaseInstanceView;
import org.jbpm.persistence.api.integration.model.ProcessInstanceView;
import org.jbpm.persistence.correlation.CorrelationKeyInfo;
import org.jbpm.persistence.correlation.CorrelationPropertyInfo;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.LockProcessInstanceException;
import org.jbpm.process.instance.ProcessInstanceManager;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.process.instance.timer.TimerManager;
import org.jbpm.workflow.core.WorkflowProcess;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.StateBasedNodeInstance;
import org.jbpm.workflow.instance.node.TimerNodeInstance;
import org.kie.api.definition.process.Process;
import org.kie.api.openicar.profiler.SimpleProfiler;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;

/**
 * This is an implementation of the {@link ProcessInstanceManager} that uses JPA.
 * </p>
 * What's important to remember here is that we have a jbpm-console which has 1 static (stateful) knowledge session
 * which is used by multiple threads: each request sent to the jbpm-console is picked up in it's own thread. 
 * </p>
 * This means that multiple threads can be using the same instance of this class. 
 */
public class JPAProcessInstanceManager
    implements
    ProcessInstanceManager {

	public static final boolean READONLY_DISABLED = Boolean.valueOf(System.getProperty("org.jbpm.persistence.processinstance.JPAProcessInstanceManager.READONLY_DISABLED", "false"));

	// slf4j вместо org.jboss.seam.log.Log
	Logger log = LoggerFactory.getLogger(getClass());
	
    private InternalKnowledgeRuntime kruntime;
    // In a scenario in which 1000's of processes are running daily,
    //   lazy initialization is more costly than eager initialization
    // Added volatile so that if something happens, we can figure out what
    private volatile transient Map<Long, ProcessInstance> processInstances = new ConcurrentHashMap<Long, ProcessInstance>();

    protected static volatile ConcurrentMap<Long, ReentrantLock> processInstanceLocks = new ConcurrentHashMap<Long, ReentrantLock>();
    protected static Queue<Long> processInstancePreLocks = new ConcurrentLinkedQueue<Long>();

    //protected volatile transient Set<Long> localProcessInstanceLocks = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    protected static ThreadLocal<Set<Long>> threadLocalProcessInstanceLocks = new ThreadLocal<Set<Long>>() {
		@Override
		protected Set<Long> initialValue() {
			return new HashSet<Long>();
		}
    };

	protected Set<Long> getLocalProcessInstanceLocks() {
		return threadLocalProcessInstanceLocks.get();
	}

    // for tests
    public static boolean hasLocks() {
    	for (ReentrantLock lock : processInstanceLocks.values()) {
    		if (lock.isLocked()) return true;
    	}
    	return false;
    }

    public static void forceReleaseLocks() {
    	synchronized (processInstanceLocks) {
        	for (Map.Entry<Long, ReentrantLock> locksEntry : new ArrayList<Map.Entry<Long, ReentrantLock>>(processInstanceLocks.entrySet())) {
        		Long processInstanceId = locksEntry.getKey();
        		ReentrantLock lock = locksEntry.getValue();
				if (lock.isLocked()) {
        			if (lock.isHeldByCurrentThread()) {
        				lock.unlock();
        				threadLocalProcessInstanceLocks.get().remove(processInstanceId);
        			} else
        				locksEntry.setValue(new ReentrantLock());
        		} else {
        			tryRemoveObsoleteLock(processInstanceId);
        		}
        	}
    	}
    }

    public void lockProcessInstance(Long processInstanceId, boolean lockParents) {
    	if (processInstanceId == null) throw new IllegalArgumentException("processInstanceId == null");
    	Set<Long> tlpil = getLocalProcessInstanceLocks();
    	if (tlpil.contains(processInstanceId) && !lockParents) {
    		return;
    	}
    	List<Long> idsToLock = getIdsToLock(tlpil, processInstanceId, lockParents);
    	if (idsToLock.isEmpty()) {
    		return;
    	}
    	if (tlpil.containsAll(idsToLock)) {
    	    return;
    	}
    	boolean success = false;
    	processInstancePreLocks.addAll(idsToLock);	// заявляем о том, что собираемся блокировать такие-то экземпляры процессов
    												// это необходимо для своевременной чистки мусора (блокировок)
    	List<String> heldLocksByAnotherThreads = new ArrayList<String>();
    	try {
			success = (useNewLockImplementation() ? lockProcessInstanceNewImpl(tlpil, idsToLock) : lockProcessInstanceOldImpl(tlpil, idsToLock));
    	} finally {
    		for (Long idToLock : idsToLock) {
    			processInstancePreLocks.remove(idToLock); // удалять строго по одному элементу!!!
    		}
    		if (!success) { // произошла ошибка или мы не смогли заблокировать
    			for (Long idToLock : idsToLock) {
    				ReentrantLock lock = processInstanceLocks.get(idToLock);
    				if (lock.isHeldByCurrentThread()) {
    					tryRemoveObsoleteLock(idToLock);
    					lock.unlock();
    				} else {
    					heldLocksByAnotherThreads.add(lock.toString()); // resolve toString here !!!
    				}
    			}
    			tlpil.removeAll(idsToLock);
    		}
    	}
		if (success)
			return;
    	String message = "Cannot lock process instances : " + idsToLock + ", processInstanceId : " + processInstanceId;
    	log.error(message + ", heldLocksByAnotherThreads: {}", heldLocksByAnotherThreads);
		throw new LockProcessInstanceException(message);
    }

    @Deprecated
	protected boolean lockProcessInstanceOldImpl(Set<Long> tlpil, List<Long> idsToLock) {
    	Random random = new Random();
		SimpleProfiler.st(" lock synchronization loop"); // profiler !!!    			
    	for (int iteration = 0; iteration < getLockIterations(); iteration++) {
    		boolean isSuccessfulLocked = false;
    		log.debug("before synchronized block for lock : {}, thread = {}", idsToLock, Thread.currentThread());
    		synchronized(processInstanceLocks) {
    			log.debug("begin synchronized block for lock : {}, thread = {}", idsToLock, Thread.currentThread());
    			boolean allIdsIsFree = true;
    			log.debug("check freedom of {}", idsToLock);
    			for (Long idToLock : idsToLock) {
    				if (tlpil.contains(idToLock)) continue;
    				ReentrantLock lock = processInstanceLocks.get(idToLock);
    				if (lock == null) continue;
    				if (lock.isLocked() && !lock.isHeldByCurrentThread()) allIdsIsFree = false;
    			}
    			log.debug("freedom of {} : {}", idsToLock, allIdsIsFree);
    			if (allIdsIsFree) {
    				for (Long idToLock : idsToLock) {
    					if (tlpil.contains(idToLock)) continue;
    					SimpleProfiler.st(" lock processInstanceId"); // profiler !!!    			
    					ReentrantLock lock = processInstanceLocks.get(idToLock);
    					if (lock == null) {
    						lock = new ReentrantLock();
    						processInstanceLocks.put(idToLock, lock);
    						log.debug("lock processInstanceId 1 : {}, thread = {}", idToLock, Thread.currentThread());
    						lock.lock();
    						tlpil.add(idToLock);
    						log.debug("processInstanceId locked : {}, thread = {}", idToLock, Thread.currentThread());
    						SimpleProfiler.en(" lock processInstanceId"); // profiler !!!
    						continue;
    					}
    					if (lock.isHeldByCurrentThread()) {
    						SimpleProfiler.en(" lock processInstanceId"); // profiler !!!
    						continue;
    					}
    					try {
    						log.debug("lock processInstanceId 2 : {}, thread = {}", idToLock, Thread.currentThread());
    						lock.tryLock(5, TimeUnit.SECONDS);
    						//Thread.sleep(500);
    					} catch (InterruptedException e) {
    						SimpleProfiler.en(" lock processInstanceId"); // profiler !!!
    						SimpleProfiler.en(" lock synchronization loop"); // profiler !!!    			
    						e.printStackTrace();
    						log.debug("interrupted synchronized block for lock : {}, thread = {}", idsToLock, Thread.currentThread());
    						throw new RuntimeException(e);
    					}
    					if (lock.isHeldByCurrentThread()) {
    						tlpil.add(idToLock);
    						log.debug("processInstanceId locked : {}, thread = {}", idToLock, Thread.currentThread());
    					}
    					SimpleProfiler.en(" lock processInstanceId"); // profiler !!!
    				}
    				isSuccessfulLocked = true;
    			}
    			log.debug("end synchronized block for lock : {}, thread = {}", idsToLock, Thread.currentThread());
    		}
    		if (isSuccessfulLocked) {
    			SimpleProfiler.en(" lock synchronization loop"); // profiler !!!    			
    			return true;
    		}
			try {
				Thread.sleep(Math.abs(random.nextLong() % 1000) + 300);
			} catch (InterruptedException e) {
				e.printStackTrace();
				SimpleProfiler.en(" lock synchronization loop"); // profiler !!!    			
				throw new RuntimeException(e);
			}
    	}
		SimpleProfiler.en(" lock synchronization loop"); // profiler !!!    			
		return false;
	}

	protected boolean lockProcessInstanceNewImpl(Set<Long> tlpil, List<Long> idsToLock) {
		Random random = new Random();
		boolean isSuccessfulLocked = false;
		SimpleProfiler.st(" lock synchronization loop"); // profiler !!!
    	try {
    		int availableLockTimeout = getMaxLockTimeoutMillis();
			int timeMultiplier = getLockIterations();
    		List<Long> idsToLockRemains = new ArrayList<Long>(idsToLock);
    		idsToLockRemains.removeAll(tlpil);
			for (Long idToLock : new ArrayList<Long>(idsToLockRemains)) {
				ReentrantLock newLock = new ReentrantLock();
				newLock.lock(); // овладеваем блокировкой заранее, на случай, когда в processInstanceLocks ее еще нет
				ReentrantLock oldLock = processInstanceLocks.putIfAbsent(idToLock, newLock);
				if (oldLock == null) { // наша блокировка первая
					tlpil.add(idToLock);
					idsToLockRemains.remove(idToLock);
					continue;
				}
				// блокировка уже есть в processInstanceLocks, попытаемся овладеть ею с таймаутом
				int timeout;
				if (availableLockTimeout <= 0) { // если время истекло, пытаемся завладеть оставшимися блокировками без ожидания
					timeout = 0;
				} else if (idsToLockRemains.size() == 1) { // последней блокировке в цепочке отдаем все оставшееся время таймаута
					timeout = availableLockTimeout;
				} else {
					timeout = 100 * (oldLock.getQueueLength() + 1); // задержка с учетом очереди
					timeout += Math.abs(random.nextLong() % 1000) + 300; // немного рандома
					timeout *= timeMultiplier; // множитель
					timeout = Math.min(availableLockTimeout, timeout); // не превышаем доступное время
				}
				long timeMillisStamp = System.currentTimeMillis();
				if (oldLock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
					tlpil.add(idToLock);
					idsToLockRemains.remove(idToLock);
					if (idsToLockRemains.isEmpty())
						break;
					if (availableLockTimeout > 0)
						availableLockTimeout -= (System.currentTimeMillis() - timeMillisStamp); // актуализируем доступное время
				} else {
					break; // не пытаемся получить следующие блокировки, если не удалось завладеть последней
				}
			}
			isSuccessfulLocked = tlpil.containsAll(idsToLock);
			if (isSuccessfulLocked)
				return true;
    	} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
    	} finally {
    		SimpleProfiler.en(" lock synchronization loop"); // profiler !!!
    	}
    	return false;
	}

	protected int getLockIterations() {
		return Integer.valueOf(System.getProperty("org.jbpm.persistence.processinstance.JPAProcessInstanceManager.LOCK_ITERATIONS", "3"));
	}

	protected boolean useNewLockImplementation() {
		return Boolean.valueOf(System.getProperty("org.jbpm.persistence.processinstance.JPAProcessInstanceManager.LOCK_NEW", "true"));
	}

	protected int getMaxLockTimeoutMillis() {
		return Integer.valueOf(System.getProperty("org.jbpm.persistence.processinstance.JPAProcessInstanceManager.LOCK_MAX_TIMEOUT", "10000"));
	}

	protected List<Long> getIdsToLock(Set<Long> tlpil, Long processInstanceId, boolean lockParents) {
		List<Long> idsToLock;
    	if (lockParents) {
            ProcessPersistenceContext context = ((ProcessPersistenceContextManager) this.kruntime.getEnvironment()
                    .get( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER ))
                    .getProcessPersistenceContext();

            String processInstanceIdsPath = context.getProcessInstanceIdsPath(processInstanceId);
        	if (processInstanceIdsPath == null)
        	    return Collections.singletonList(processInstanceId); //Collections.emptyList();

        	String[] ids = processInstanceIdsPath.split(Pattern.quote(ProcessInstanceImpl.PROCESS_INSTANCE_IDS_PATH_SEPARATOR));
        	idsToLock = new ArrayList<Long>(ids.length);
        	boolean allIdsThreadLocalLocked = true;
        	// собираем список id в обратном порядке
        	for (int i = ids.length - 1; i >= 0; i--) {
        		String idString = ids[i];
				Long idToLock = Long.valueOf(idString);
				idsToLock.add(idToLock);
        		if (!tlpil.contains(idToLock))
        			allIdsThreadLocalLocked = false;
        	}
        	if (allIdsThreadLocalLocked)
        		idsToLock.clear();
    	} else {
    		idsToLock = Collections.singletonList(processInstanceId);
    	}
		return idsToLock;
	}

    public void unlockProcessInstance(Long processInstanceId) {
    	if (processInstanceId == null) throw new IllegalArgumentException("processInstanceId == null");
		ReentrantLock lock = processInstanceLocks.get(processInstanceId);
		if (lock == null || !lock.isHeldByCurrentThread()/* && !getLocalProcessInstanceLocks().contains(processInstanceId)*/) {
			return;
		}
    	SimpleProfiler.st(" unlock processInstanceId"); // profiler !!!
		log.debug("unlock processInstanceId : {}, thread = {}", processInstanceId, Thread.currentThread());
		getLocalProcessInstanceLocks().remove(processInstanceId);
    	SimpleProfiler.st(" reentrant unlock"); // profiler !!!
    	tryRemoveObsoleteLock(processInstanceId);
		lock.unlock();
		//if (lock.isHeldByCurrentThread())
		//	lock.unlock();
		//else if (getLocalProcessInstanceLocks().contains(processInstanceId))
		//	processInstanceLocks.put(processInstanceId, new ReentrantLock());
    	SimpleProfiler.en(" reentrant unlock"); // profiler !!!
		log.debug("processInstanceId unlocked : {}, thread = {}", processInstanceId, Thread.currentThread());
		SimpleProfiler.en(" unlock processInstanceId"); // profiler !!!
    }

    protected static void tryRemoveObsoleteLock(Long processInstanceId) {
		ReentrantLock lock = processInstanceLocks.get(processInstanceId);
		if (lock == null || lock.hasQueuedThreads())
			return;
		boolean needUnlock = !lock.isHeldByCurrentThread() && lock.tryLock();
    	try {
    		if (processInstancePreLocks.contains(processInstanceId) ||
    				lock.hasQueuedThreads() ||
    				!lock.isHeldByCurrentThread() && lock.isLocked())
    			return;
    	} finally {
    		if (needUnlock) lock.unlock();
    	}
		// удаляем блокировку, когда в ней никто не заинтересован
		processInstanceLocks.remove(processInstanceId);
    }

    public void setKnowledgeRuntime(InternalKnowledgeRuntime kruntime) {
        this.kruntime = kruntime;
    }

    public void addProcessInstance(ProcessInstance processInstance, CorrelationKey correlationKey) {
        ProcessInstanceInfo processInstanceInfo = new ProcessInstanceInfo( processInstance, this.kruntime.getEnvironment() );
        ProcessPersistenceContext context 
            = ((ProcessPersistenceContextManager) this.kruntime.getEnvironment()
                    .get( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER ))
                    .getProcessPersistenceContext();

        processInstanceInfo = (ProcessInstanceInfo) context.persist( processInstanceInfo );

        log.debug("addProcessInstance : id = {}, thread = {}", processInstanceInfo.getId(), Thread.currentThread());
        lockProcessInstance(processInstanceInfo.getId(), false);

        ((org.jbpm.process.instance.ProcessInstance) processInstance).setId( processInstanceInfo.getId() );
        ((ProcessInstanceImpl)processInstance).setProcessInstanceIdsPath(processInstanceInfo.getId().toString());
        processInstanceInfo.updateLastReadDate();
        // generate correlation key if not given which is same as process instance id to keep uniqueness 
        if (correlationKey == null) {
            correlationKey = new CorrelationKeyInfo();
            ((CorrelationKeyInfo) correlationKey).addProperty(new CorrelationPropertyInfo(null, processInstanceInfo.getId().toString()));
            ((CorrelationKeyInfo) correlationKey).setName(correlationKey.toExternalForm());
            ((org.jbpm.process.instance.ProcessInstance) processInstance).getMetaData().put("CorrelationKey", correlationKey);
        }
        CorrelationKeyInfo correlationKeyInfo = (CorrelationKeyInfo) correlationKey;
        correlationKeyInfo.setProcessInstanceId(processInstanceInfo.getId());
        context.persist(correlationKeyInfo);
        internalAddProcessInstance(processInstance);
        
        EventManagerProvider.getInstance().get().create(getInstanceViewFor(processInstance));
    }
    
    public void internalAddProcessInstance(ProcessInstance processInstance) {
        if( ((ConcurrentHashMap<Long, ProcessInstance>) processInstances)
                .putIfAbsent(processInstance.getId(), processInstance) 
                != null ) { 
            throw new ConcurrentModificationException(
                    "Duplicate process instance [" + processInstance.getProcessId() + "/" + processInstance.getId() + "]"
                    + " added to process instance manager." );
        }
    }

    public ProcessInstance getProcessInstance(long id) {
        return getProcessInstance(id, false);
    }

    public ProcessInstance getProcessInstance(long id, boolean readOnly) {
        log.debug("getProcessInstance : id = {}, thread = {}", id, Thread.currentThread());

        InternalRuntimeManager manager = (InternalRuntimeManager) kruntime.getEnvironment().get(EnvironmentName.RUNTIME_MANAGER);
        if (manager != null) {
            manager.validate((KieSession) kruntime, ProcessInstanceIdContext.get(id));
        }
        TransactionManager txm = (TransactionManager) this.kruntime.getEnvironment().get( EnvironmentName.TRANSACTION_MANAGER );

        if (READONLY_DISABLED)
            readOnly = false;

        if (!readOnly) {
            lockProcessInstance(id, true);
        }

        org.jbpm.process.instance.ProcessInstance processInstance = null;
        processInstance = (org.jbpm.process.instance.ProcessInstance) this.processInstances.get(id);
        if (processInstance != null) {
            if (processInstance.getKnowledgeRuntime() == null) {
                log.warn("processInstance.kruntime is null, i'll try to fix this... stackTrace:\r\n{}", getStackTracePrint());
                processInstance.setKnowledgeRuntime(kruntime);
            }
            if (((WorkflowProcessInstanceImpl) processInstance).isPersisted() && !readOnly) {
            	ProcessPersistenceContextManager ppcm 
        	    = (ProcessPersistenceContextManager) this.kruntime.getEnvironment().get( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER );
            	ppcm.beginCommandScopedEntityManager();
            	ProcessPersistenceContext context = ppcm.getProcessPersistenceContext();
            	SimpleProfiler.st(" fetch ProcessInstanceInfo"); // profiler !!!
                ProcessInstanceInfo processInstanceInfo = (ProcessInstanceInfo) context.findProcessInstanceInfo( id );
                SimpleProfiler.en(" fetch ProcessInstanceInfo"); // profiler !!!
                if ( processInstanceInfo == null ) {
                    log.debug("ProcessInstanceInfo not found : {}, stackTrace:\r\n{}", id, getStackTracePrint());
                   	unlockProcessInstance(id);
                    return null;
                }  
                TransactionManagerHelper.addToUpdatableSet(txm, processInstanceInfo);
                processInstanceInfo.updateLastReadDate();
                

                EventManagerProvider.getInstance().get().update(getInstanceViewFor(processInstance));
  
            }
        	return processInstance;
        }
        try {

            SimpleProfiler.st(" restore ProcessInstance from db"); // profiler !!!

        	// Make sure that the cmd scoped entity manager has started
        	ProcessPersistenceContextManager ppcm 
        	    = (ProcessPersistenceContextManager) this.kruntime.getEnvironment().get( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER );
        	ppcm.beginCommandScopedEntityManager();
            EntityManager cmdScopedEntityManager = ((JpaProcessPersistenceContextManager)ppcm).getCommandScopedEntityManager();

            ProcessPersistenceContext context = ppcm.getProcessPersistenceContext();
            SimpleProfiler.st(" fetch ProcessInstanceInfo"); // profiler !!!
            ProcessInstanceInfo processInstanceInfo = (ProcessInstanceInfo) context.findProcessInstanceInfo( id );
            SimpleProfiler.en(" fetch ProcessInstanceInfo"); // profiler !!!
            if ( processInstanceInfo == null ) {
                SimpleProfiler.en(" restore ProcessInstance from db"); // profiler !!!
                log.debug("ProcessInstanceInfo not found : {}, stackTrace:\r\n{}", id, getStackTracePrint());
                if (!readOnly)
                	unlockProcessInstance(id);
                return null;
            }
            SimpleProfiler.st(" read ProcessInstance from ProcessInstanceInfo"); // profiler !!!
            processInstance = (org.jbpm.process.instance.ProcessInstance)
            	processInstanceInfo.getProcessInstance(kruntime, this.kruntime.getEnvironment(), readOnly);
            SimpleProfiler.en(" read ProcessInstance from ProcessInstanceInfo"); // profiler !!!
            if (!readOnly) {
                processInstanceInfo.updateLastReadDate();
                TransactionManagerHelper.addToUpdatableSet(txm, processInstanceInfo);            
            }
            SimpleProfiler.en(" restore ProcessInstance from db"); // profiler !!!
            if (((ProcessInstanceImpl) processInstance).getProcessXml() == null) {
    	        Process process = kruntime.getKieBase().getProcess( processInstance.getProcessId() );
    	        if ( process == null ) {
                    if (!readOnly)
                	    unlockProcessInstance(id);
    	            throw new IllegalArgumentException( "Could not find process " + processInstance.getProcessId() );
    	        }
    	        processInstance.setProcess( process );
            }
            Long parentProcessInstanceId = (Long) ((ProcessInstanceImpl) processInstance).getMetaData().get(ProcessInstanceImpl.PARENT_PROCESS_INSTANCE_ID_METADATA);
            if (parentProcessInstanceId != null) {
                kruntime.getProcessInstance(parentProcessInstanceId, readOnly);
            }
            if ( processInstance.getKnowledgeRuntime() == null && !readOnly ) {
                processInstance.setKnowledgeRuntime( kruntime );
                ((ProcessInstanceImpl) processInstance).reconnect();
            }
            if (readOnly) {
                internalRemoveProcessInstance(processInstance);
                cmdScopedEntityManager.detach(processInstanceInfo); // removing from cache - it needs to use fresh ProcessInstanceInfo version in the next write-mode access
                                                                    // that fixes exception "Row was updated or deleted by another transaction"
            }
            return processInstance;
        } finally {
            if (!readOnly && processInstance != null) {
                EventManagerProvider.getInstance().get().update(getInstanceViewFor(processInstance));
            }
        }
    }

    protected String getStackTracePrint() {
        StringWriter sw = new StringWriter();
        new Exception().printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return Collections.unmodifiableCollection(processInstances.values());
    }

    public void removeProcessInstance(ProcessInstance processInstance) {
        ProcessPersistenceContext context = ((ProcessPersistenceContextManager) this.kruntime.getEnvironment().get( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER )).getProcessPersistenceContext();
        ProcessInstanceInfo processInstanceInfo = (ProcessInstanceInfo) context.findProcessInstanceInfo( processInstance.getId() );
        
        if ( processInstanceInfo != null ) {
            context.remove( processInstanceInfo );
        }
        internalRemoveProcessInstance(processInstance);
        
        EventManagerProvider.getInstance().get().delete(getInstanceViewFor(processInstance));
    }

    public void internalRemoveProcessInstance(ProcessInstance processInstance) {
        //log.debug("internalRemoveProcessInstance : id = {}, thread = {}", processInstance.getId(), Thread.currentThread());
        processInstances.remove( processInstance.getId() );
    }
    
    public void clearProcessInstances() {
        for (ProcessInstance processInstance: new ArrayList<ProcessInstance>(processInstances.values())) {
            ((ProcessInstanceImpl) processInstance).disconnect();
        }

    	log.debug("clearProcessInstances... unlock processes : {}", getLocalProcessInstanceLocks());
        for (Long processInstanceId : new ArrayList<Long>(getLocalProcessInstanceLocks())) {			
        	unlockProcessInstance(processInstanceId);
		}
         
    }

    public void clearProcessInstancesState() {
        try {
            // at this point only timers are considered as state that needs to be cleared
            TimerManager timerManager = ((InternalProcessRuntime)kruntime.getProcessRuntime()).getTimerManager();
            
            for (ProcessInstance processInstance: new ArrayList<ProcessInstance>(processInstances.values())) {
                WorkflowProcessInstance pi = ((WorkflowProcessInstance) processInstance);
    
                
                for (org.kie.api.runtime.process.NodeInstance nodeInstance : pi.getNodeInstances()) {
                    if (nodeInstance instanceof TimerNodeInstance){
                        if (((TimerNodeInstance)nodeInstance).getTimerInstance() != null) {
                            timerManager.cancelTimer(((TimerNodeInstance)nodeInstance).getTimerInstance().getId());
                        }
                    } else if (nodeInstance instanceof StateBasedNodeInstance) {
                        List<Long> timerIds = ((StateBasedNodeInstance) nodeInstance).getTimerInstances();
                        if (timerIds != null) {
                            for (Long id: timerIds) {
                                timerManager.cancelTimer(id);
                            }
                        }
                    }
                }
                
            }
        } catch (Exception e) {
            // catch everything here to make sure it will not break any following 
            // logic to allow complete clean up 
        }
    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {
        ProcessPersistenceContext context = ((ProcessPersistenceContextManager) this.kruntime.getEnvironment()
                .get( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER ))
                .getProcessPersistenceContext();
        Long processInstanceId = context.getProcessInstanceByCorrelationKey(correlationKey);
        if (processInstanceId == null) {
            return null;
        }
        return getProcessInstance(processInstanceId);
    }
    
    protected InstanceView<ProcessInstance> getInstanceViewFor(ProcessInstance pi) {
        if (((WorkflowProcess)pi.getProcess()).isDynamic()) {
            return new CaseInstanceView(pi);
        }
        
        return new ProcessInstanceView(pi);
    }

}
