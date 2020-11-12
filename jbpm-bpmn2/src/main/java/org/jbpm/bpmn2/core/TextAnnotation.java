package org.jbpm.bpmn2.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:a.fomichev@comsoft-corp.ru">Fomichev Artem</a>
 */
public class TextAnnotation implements Serializable {

	private static final long serialVersionUID = 510l;

	private String id;
	private String text;
	private Map<String, Object> metaData = new HashMap<String, Object>();

	public String getId() {
		return id;
	}

	public String setId(String id) {
		return this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Map<String, Object> getMetaData() {
		return metaData;
	}

	public void setMetaData(Map<String, Object> metaData) {
		this.metaData = metaData;
	}
}
