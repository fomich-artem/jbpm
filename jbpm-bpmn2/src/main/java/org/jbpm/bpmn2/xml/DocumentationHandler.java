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

package org.jbpm.bpmn2.xml;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.drools.core.xml.BaseAbstractHandler;
import org.drools.core.xml.ExtensibleXmlParser;
import org.drools.core.xml.Handler;
import org.jbpm.bpmn2.core.Definitions;
import org.jbpm.bpmn2.core.Lane;
import org.jbpm.bpmn2.core.TextAnnotation;
import org.jbpm.compiler.xml.ProcessBuildData;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class DocumentationHandler extends BaseAbstractHandler implements Handler {
	
	@SuppressWarnings("unchecked")
	public DocumentationHandler() {
		if ((this.validParents == null) && (this.validPeers == null)) {
			this.validParents = new HashSet();
			this.validParents.add(Object.class);

			this.validPeers = new HashSet();
			this.validPeers.add(null);
            this.validPeers.add(Object.class);

			this.allowNesting = false;
		}
	}

    public Object start(final String uri,
			            final String localName,
			            final Attributes attrs,
			            final ExtensibleXmlParser parser) throws SAXException {
		parser.startElementBuilder( localName, attrs );
		return null;
	}    

	public Object end(final String uri, final String localName,
			          final ExtensibleXmlParser parser) throws SAXException {
		Element element = parser.endElementBuilder();
		Object parent = parser.getParent();
        boolean isNode = parent instanceof NodeImpl;
        boolean isProcess = parent instanceof RuleFlowProcess;
        boolean isDefinitions = parent instanceof Definitions;
        boolean isTextAnnotation = parent instanceof TextAnnotation;
        boolean isLane = parent instanceof Lane;
        if (isNode || isProcess || isDefinitions || isTextAnnotation || isLane) {
	        String text = ((Text)element.getChildNodes().item( 0 )).getWholeText();
	        if (text != null) {
	            text = text.trim();
	            if ("".equals(text)) {
	                text = null;
	            }
	        }
            if (text != null) {
                if (isDefinitions) {
                    ((ProcessBuildData) parser.getData()).setMetaData("Documentation", text);
                    // пишем документацию, если это еще не сделано
                    List<org.kie.api.definition.process.Process> processes = ((ProcessBuildData) parser.getData()).getProcesses();
                    for (org.kie.api.definition.process.Process process : processes)
                        if (((RuleFlowProcess) process).getMetaData("Documentation") == null)
                            ((RuleFlowProcess) process).getMetaData().put("Documentation", text);
                } else {
                    Map<String, Object> metaData;
                    if (isNode)
                        metaData = ((NodeImpl) parent).getMetaData();
                    else if (isTextAnnotation)
                        metaData = ((TextAnnotation) parent).getMetaData();
                    else  if (isLane)
                        metaData = ((Lane) parent).getMetaData();
                    else
                        metaData = ((RuleFlowProcess) parent).getMetaData();
                    metaData.put("Documentation", text);
                }
            }
		}
		return parser.getCurrent();
	}

	public Class<?> generateNodeFor() {
		return null;
	}

}
