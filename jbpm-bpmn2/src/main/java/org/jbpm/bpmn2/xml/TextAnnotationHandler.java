package org.jbpm.bpmn2.xml;

import org.drools.core.xml.BaseAbstractHandler;
import org.drools.core.xml.ExtensibleXmlParser;
import org.drools.core.xml.Handler;
import org.jbpm.bpmn2.core.Association;
import org.jbpm.bpmn2.core.Lane;
import org.jbpm.bpmn2.core.SequenceFlow;
import org.jbpm.bpmn2.core.TextAnnotation;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.NodeContainer;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 
 * @author <a href="mailto:faa@comsoft-corp.ru">Фомичев Артем</a> <br>
 *
 */
public class TextAnnotationHandler extends BaseAbstractHandler implements Handler {

    public static final String ANNOTATIONS = "BPMN.TextAnnotations";

    public TextAnnotationHandler() {
        if ((this.validParents == null) && (this.validPeers == null)) {
            this.validParents = new HashSet<Class<?>>();
            this.validParents.add(RuleFlowProcess.class);

            this.validPeers = new HashSet<Class<?>>();
            this.validPeers.add(null);
            this.validPeers.add(null);
            this.validPeers.add(Lane.class);
            this.validPeers.add(Variable.class);
            this.validPeers.add(Node.class);
            this.validPeers.add(SequenceFlow.class);
            this.validPeers.add(Lane.class);
            this.validPeers.add(Association.class);
            this.validPeers.add(TextAnnotation.class);

            this.allowNesting = false;
        }
    }

    public Object start(final String uri, final String localName,
                        final Attributes attrs, final ExtensibleXmlParser parser)
            throws SAXException {
        parser.startElementBuilder(localName, attrs);

        NodeContainer nodeContainer = (NodeContainer) parser.getParent();

        List<TextAnnotation> connections = null;
        RuleFlowProcess process = (RuleFlowProcess) nodeContainer;
        connections = (List<TextAnnotation>) process.getMetaData(TextAnnotationHandler.ANNOTATIONS);
        if (connections == null) {
            connections = new ArrayList<TextAnnotation>();
            process.setMetaData(TextAnnotationHandler.ANNOTATIONS, connections);
        }

        TextAnnotation annotation = new TextAnnotation();
        annotation.setId(attrs.getValue("id"));
        connections.add(annotation);

        return annotation;
    }

    public Object end(final String uri, final String localName,
                      final ExtensibleXmlParser parser) throws SAXException {
        final Element element = parser.endElementBuilder();
        TextAnnotation annotation = (TextAnnotation) parser.getCurrent();

        org.w3c.dom.Node xmlNode = element.getFirstChild();
        while (xmlNode != null) {

            String nodeName = xmlNode.getNodeName();
            if ("text".equals(nodeName)) {
                String annotationText = xmlNode.getTextContent();
                annotation.setText(annotationText);
            }
            xmlNode = xmlNode.getNextSibling();
        }
        return annotation;
    }

    public Class<?> generateNodeFor() {
        return TextAnnotation.class;
    }
}
