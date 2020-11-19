
package org.jbpm.openicar.seamel;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.mvel2.integration.VariableResolverFactory;

/**
 * Variable bindings for Seam EL expressions, that provides process variable scope
 *
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 *
 */
public class SeamELVariableBindings extends SimpleBindings implements Bindings {

	private static final UnsupportedOperationException NOT_SUPPORTED_OPERATION = new UnsupportedOperationException("Not supported operation");

	private VariableResolverFactory variableResolverFactory;

	/**
	 * @param nodeInstance
	 */
	public SeamELVariableBindings(VariableResolverFactory variableResolverFactory) {
		super();
		this.variableResolverFactory = variableResolverFactory;
	}

	public int size() {
		throw NOT_SUPPORTED_OPERATION;
	}

	public boolean isEmpty() {
		return false;
	}

	public boolean containsValue(Object value) {
		throw NOT_SUPPORTED_OPERATION;
	}

	public void clear() {
		throw NOT_SUPPORTED_OPERATION;
	}

	public Set<String> keySet() {
		throw NOT_SUPPORTED_OPERATION;
	}

	public Collection<Object> values() {
		throw NOT_SUPPORTED_OPERATION;
	}

	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		throw NOT_SUPPORTED_OPERATION;
	}

/*	public Object put(String name, Object value) {
		Object prevValue = nodeInstance.getVariable(name);
		nodeInstance.setVariable(name, value);
		return prevValue;
	}
*/
	public void putAll(Map<? extends String, ? extends Object> toMerge) {
		throw NOT_SUPPORTED_OPERATION;
	}

	public boolean containsKey(Object key) {
		boolean result = super.containsKey(key);
		if (!result) result = key instanceof String ? variableResolverFactory.isResolveable((String) key) : false;
		return result;
	}

	public Object get(Object key) {
		Object result = super.get(key);
		if (result == null && key instanceof String) return variableResolverFactory.getVariableResolver((String) key).getValue();
		return result;
	}

	public Object remove(Object key) {
		throw NOT_SUPPORTED_OPERATION;
	}

}
