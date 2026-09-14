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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.el.ArrayELResolver;
import javax.el.BeanELResolver;
import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.ListELResolver;
import javax.el.MapELResolver;
import javax.el.ResourceBundleELResolver;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 */
public class SeamELScriptEngine /*extends AbstractScriptEngine*/ {

	// slf4j вместо org.jboss.seam.log.Log
	Logger log = LoggerFactory.getLogger(getClass());
	
	private ExpressionFactory exprFactory;

	public static SeamELScriptEngine instance() {
		return new SeamELScriptEngine();
	}

	/**
	 * Де-Сим хук (mosaek-порт, 2026-09-14): резолв идентификатора EL, когда
	 * Seam-контекст неактивен. В каноне `#{componentName.property}` резолвится
	 * через Seam Context/Init (SeamELResolver.resolveBase →
	 * Init.getRootNamespace().getComponentInstance); в порт-окружении без
	 * bootstrap'а Seam это молча даёт null. Хук вызывается ПОСЛЕ проверки
	 * script-биндингов (процессные переменные имеют приоритет — канонный
	 * порядок VariableMapper → ELResolver сохраняется) и до fallback'а в Seam.
	 * Порт регистрирует резолвер Spring-бинов при старте контекста
	 * (SeamElBeanBridge); НЕ тащить обратно в ekie/canon.
	 */
	public interface BeanResolver {
		Object resolve(String name);
	}

	private static volatile BeanResolver beanResolver;

	public static void setBeanResolver(BeanResolver resolver) {
		beanResolver = resolver;
	}

	public static BeanResolver getBeanResolver() {
		return beanResolver;
	}

	public SeamELScriptEngine()
	{
		// Де-Сим (mosaek, 2026-09-14): вместо SeamExpressionFactory (Seam-цепочка
		// резолверов) — стандартная фабрика EL. Имплементацию находит ServiceLoader'ом
		// (в порт-окружении mosaek — tomcat-embed-el / jboss-el из classpath).
		// Идентификаторы резолвятся VariableMapper'ом (script-биндинги процессов +
		// хук BeanResolver), базовые свойства — стандартным BeanELResolver (см.
		// WrappedSeamELContext). НЕ тащить обратно в ekie/canon.
		this.exprFactory = ExpressionFactory.newInstance();
	}

	public Bindings createBindings() {
		return new SimpleBindings();
	}

	public Object eval(String script, ScriptContext ctx) throws ScriptException {
		if (script != null) script = script.trim();
		log.debug("EVALUATE 1: {}", script);
		ELContext context = toELContext(ctx);
		ValueExpression valueExpression = parse(script, context);
		return evalExpr(valueExpression, context);
	}

	public Object eval(Reader reader, ScriptContext ctx) throws ScriptException {
		log.debug("EVALUATE 2: {}", reader);
		return eval(readFully(reader), ctx);
	}

	private ELContext toELContext(final ScriptContext ctx)
	{
		log.debug("PERFORM toELContext");

		Object tmp = ctx.getAttribute("elcontext");

		if (tmp instanceof ELContext)
		{
			return ((ELContext)tmp);
		}

		ctx.setAttribute("context", ctx, ScriptContext.ENGINE_SCOPE);

		ctx.setAttribute("out:print", getPrintMethod(), ScriptContext.ENGINE_SCOPE);

		SecurityManager manager = System.getSecurityManager();

		if (manager == null)
		{
			ctx.setAttribute("lang:import", getImportMethod(), ScriptContext.ENGINE_SCOPE);
		}

		ELContext elContext = new WrappedSeamELContext(ctx);

		ctx.setAttribute("elcontext", elContext, ScriptContext.ENGINE_SCOPE);

		return elContext;
	}


	private class WrappedSeamELContext extends ELContext {
//		private final ScriptContext ctx;
		private VariableMapper varMapper;
		private FunctionMapper funcMapper;

		private WrappedSeamELContext(ScriptContext ctx) {
//			this.ctx = ctx;
			// Де-Сим (mosaek): родительские mapper'ы Seam-контекста больше не
			// существуют; код ниже рассчитан на null-parent (как и раньше —
			// в порт-окружении они были no-op)
			varMapper = new VariableMapperImpl(ctx, null);
			funcMapper = new FunctionMapperImpl(ctx, null);
		}

		@Override
		public VariableMapper getVariableMapper() {
			return varMapper;
		}

		@Override
		public FunctionMapper getFunctionMapper() {
			return funcMapper;
		}

		// Де-Сим (mosaek): вместо Seam-ELResolver-цепочки (Contexts/Init) —
		// стандартные резолверы EL. Идентификаторы `#{name}` резолвятся
		// VariableMapper'ом (script-биндинги + BeanResolver-хук), доступ
		// к свойствам `#{bean.prop}` — BeanELResolver'ом.
		@Override
		public ELResolver getELResolver() {
			CompositeELResolver resolver = new CompositeELResolver();
			resolver.add(new ArrayELResolver(true));
			resolver.add(new ListELResolver(true));
			resolver.add(new MapELResolver(true));
			resolver.add(new ResourceBundleELResolver());
			resolver.add(new BeanELResolver(true));
			return resolver;
		}
	}

	private class FunctionMapperImpl extends FunctionMapper
	{
		private ScriptContext ctx;
		private FunctionMapper parentFunctionMapper;

		FunctionMapperImpl(ScriptContext ctx, FunctionMapper parentFunctionMapper)
		{
			this.ctx = ctx;
			this.parentFunctionMapper = parentFunctionMapper;
		}

		private String getFullName(String prefix, String localName)
		{
			return prefix + ":" + localName;
		}

		public Method _resolveFunction(String prefix, String localName)
		{
			String fullName = getFullName(prefix, localName);

			int scope = this.ctx.getAttributesScope(fullName);

			if (scope != -1)
			{
				Object tmp = this.ctx.getAttribute(fullName);

				return ((tmp instanceof Method) ? (Method)tmp : null);
			}

			return null;
		}

		@Override
		public Method resolveFunction(String prefix, String localName) {
			Method result = _resolveFunction(prefix, localName);
			if (result == null && parentFunctionMapper != null) result = parentFunctionMapper.resolveFunction(prefix, localName);
			return result;
		}
	}


	private class VariableMapperImpl extends VariableMapper
	{
		private ScriptContext ctx;
		private VariableMapper parentVariableMapper;

		VariableMapperImpl(ScriptContext ctx, VariableMapper parentVariableMapper)
		{
			this.ctx = ctx;
			this.parentVariableMapper = parentVariableMapper;
		}

		public ValueExpression _resolveVariable(String variable)
		{
			int scope = this.ctx.getAttributesScope(variable);

			if (scope != -1)
			{
				Object value = this.ctx.getAttribute(variable, scope);

				log.debug("RESOLVED VALUE = [{}]", value);
				log.debug("VALUE CLASS: {}", (value != null ? value.getClass() : null));

				if (value instanceof ValueExpression)
				{
					return ((ValueExpression)value);
				}

				if (value == null) return null;

				log.debug("RESOLVED VALUE IS NOT VALUE EXPRESSION");

				value = resolveEntity(variable, value);

				return exprFactory.createValueExpression(value, Object.class);
			}

			// де-Сим хук (mosaek): идентификатор не в скрипт-биндингах —
			// пробуем локатор бинов до fallback'а в Seam-контекст
			BeanResolver resolver = beanResolver;
			if (resolver != null)
			{
				Object bean = resolver.resolve(variable);

				if (bean != null)
				{
					log.debug("RESOLVED VIA BEAN RESOLVER = [{}]", bean);
					return exprFactory.createValueExpression(bean, Object.class);
				}
			}

			return null;
		}

		private Object resolveEntity(String variable, Object value) {
			if (value instanceof ValueExpression) return value;

			// TODO: resolve entity
			
			return value;
		}

		@Override
		public ValueExpression resolveVariable(String variable) {
			log.debug("RESOLVE VARIABLE [{}]", variable);

			ValueExpression result = _resolveVariable(variable);
			if (result == null && parentVariableMapper != null) result = parentVariableMapper.resolveVariable(variable);
			return result;
		}

		@Override
		public ValueExpression setVariable(String variable, ValueExpression value)
		{
			log.debug("SET VARIABLE [{}] with value = {}", variable, value);

			if (parentVariableMapper != null) {
				return parentVariableMapper.setVariable(variable, value);
			}

			ValueExpression oldValue = resolveVariable(variable);
			this.ctx.setAttribute(variable, value, ScriptContext.ENGINE_SCOPE);
			return oldValue;
		}

	}

	private ValueExpression parse(String script, ELContext context) throws ScriptException
	{
		try
		{
			log.debug("PARSE SCRIPT: {}", script);
			return this.exprFactory.createValueExpression(context, script, Object.class);
		}
		catch (ELException elexp)
		{
			throw new ScriptException(elexp);
		}
	}

	private Object evalExpr(ValueExpression expr, ELContext context)
	throws ScriptException
	{
		try
		{
			log.debug("EVALUALTE EXPRESSION: {}", (expr != null ? expr.getExpressionString() : ""));
			return expr.getValue(context);
		}
		catch (ELException elexp)
		{
			throw new ScriptException(elexp);
		}
	}

	private String readFully(Reader reader)
	throws ScriptException
	{
		int numChars;
		char[] arr = new char[8192];

		StringBuilder text = new StringBuilder();
		try
		{
			while ((numChars = reader.read(arr, 0, arr.length)) > 0)
			{
				text.append(arr, 0, numChars);
			}

		}
		catch (IOException exp)
		{
			throw new ScriptException(exp);
		}

		return text.toString();
	}

	private static Method getPrintMethod()
	{
		Class<?> myClass;
		try
		{
			myClass = SeamELScriptEngine.class;

			Method method = myClass.getMethod("print", new Class[] { Object.class });

			return method;
		}
		catch (Exception exp)
		{
		}

		return null;
	}

	public static void print(Object obj)
	{
		System.out.print("PRINT OBJECT: " + obj);
	}

	private static Method getImportMethod()
	{
		Class<?> myClass;
		try
		{
			myClass = SeamELScriptEngine.class;

			Method method = myClass.getMethod("importFunctions", new Class[] { ScriptContext.class, String.class, Object.class });

			return method;
		}
		catch (Exception exp)
		{
		}

		return null;
	}

	public static void importFunctions(ScriptContext ctx, String namespace, Object obj)
	{
		Class<?> clazz = null;

		if (obj instanceof Class)
		{
			clazz = (Class<?>)obj;
		} else {
			if (obj instanceof String)
			{
				try
				{
					clazz = Class.forName((String)obj);
				}
				catch (ClassNotFoundException cnfe)
				{
					throw new ELException(cnfe);
				}

			}

			throw new ELException("Class or class name is missing");
		}

		Method[] methods = clazz.getMethods();

		for (Method m : methods)
		{
			int mod = m.getModifiers();

			if ((Modifier.isStatic(mod)) && (Modifier.isPublic(mod)))
			{
				String name = namespace + ":" + m.getName();

				ctx.setAttribute(name, m, ScriptContext.ENGINE_SCOPE);
			}
		}
	}

}
