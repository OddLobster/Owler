package eu.ows.owler.util;

import com.digitalpebble.stormcrawler.Metadata;
import java.util.ArrayList;
import java.util.List;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Normalises the robots instructions provided by the HTML meta tags or the HTTP X-Robots-Tag
 * headers.
 */
public class RobotsTags {

    enum ROBOTS_DIRECTIVES {
        noai,
        noarchive,
        nocache,
        nofollow,
        noimageai,
        noimageindex,
        noindex,
        noml,
        nosnippet,
        notranslate,
        none
    }

    private List<String> robotsDirectives = new ArrayList<>();

    private static final XPathExpression expression;

    static {
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            expression = xpath.compile("/HTML/*/META");
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get the values from the fetch metadata * */
    public void extractHTTPHeaders(Metadata metadata, String protocolMDprefix) {
        // HTTP headers
        // X-Robots-Tag: noindex
        String[] values = metadata.getValues("X-Robots-Tag", protocolMDprefix);
        if (values == null) return;
        if (values.length == 1) {
            // just in case they put all the values on a single line
            values = values[0].split(" *, *");
        }
        parseValues(values);
    }

    // set the values based on the meta tags
    // HTML tags
    // <meta name="robots" content="noarchive, nofollow"/>
    // called by the parser bolts
    public void extractMetaTags(DocumentFragment doc) throws XPathExpressionException {
        NodeList nodes = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);
        if (nodes == null) return;
        int numNodes = nodes.getLength();
        for (int i = 0; i < numNodes; i++) {
            Node n = (Node) nodes.item(i);
            // iterate on the attributes
            // and check that it has name=robots and content
            // whatever the case is
            boolean isRobots = false;
            String content = null;
            NamedNodeMap attrs = n.getAttributes();
            for (int att = 0; att < attrs.getLength(); att++) {
                Node keyval = attrs.item(att);
                if ("name".equalsIgnoreCase(keyval.getNodeName())
                        && "robots".equalsIgnoreCase(keyval.getNodeValue())) {
                    isRobots = true;
                    continue;
                }
                if ("content".equalsIgnoreCase(keyval.getNodeName())) {
                    content = keyval.getNodeValue();
                    continue;
                }
            }

            if (isRobots && content != null) {
                // got a value - split it
                String[] vals = content.split(" *, *");
                parseValues(vals);
                return;
            }
        }
    }

    /** Extracts meta tags based on the value of the content attribute * */
    public void extractMetaTags(String content) {
        if (content == null) return;
        String[] vals = content.split(" *, *");
        parseValues(vals);
    }

    private void parseValues(String[] values) {
        for (String v : values) {
            v = v.trim();
            for (ROBOTS_DIRECTIVES r : ROBOTS_DIRECTIVES.values()) {
                if (r.name().equalsIgnoreCase(v)) {
                    switch (r) {
                        case none:
                            robotsDirectives.add(ROBOTS_DIRECTIVES.noindex.name());
                            robotsDirectives.add(ROBOTS_DIRECTIVES.nofollow.name());
                            robotsDirectives.add(ROBOTS_DIRECTIVES.nocache.name());
                            break;
                        default:
                            robotsDirectives.add(r.name());
                            break;
                    }
                }
            }
        }
    }

    /** Adds a normalised representation of the directives in the metadata * */
    public void normaliseToMetadata(Metadata metadata) {
        for (final String directive : robotsDirectives) {
            metadata.addValue("robots", directive);
        }
    }

    public boolean isNoIndex() {
        return robotsDirectives.contains(ROBOTS_DIRECTIVES.noindex.name());
    }

    public boolean isNoFollow() {
        return robotsDirectives.contains(ROBOTS_DIRECTIVES.nofollow.name());
    }
}
