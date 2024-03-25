import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import eu.ows.owler.bolt.HITSBolt;
import java.util.Map;
import java.util.Set;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HITSBoltTest {

    @Mock private OutputCollector collector;

    @Mock private Tuple inputTuple;

    @Mock private TopologyContext context;

    private HITSBolt hitsBolt;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        hitsBolt = new HITSBolt();
        hitsBolt.prepare(null, context, collector);
    }

    @Test
    public void testAddLinkToGraph() {
        String fromUrl = "http://example.com";
        String toUrl = "http://example.com/page1";
        String secondToUrl = "http://example.com/page2";

        hitsBolt.addLinkToGraph(fromUrl, toUrl);

        Map<String, Set<String>> links = hitsBolt.getLinks();
        Map<String, Double> authorities = hitsBolt.getAuthorities();
        Map<String, Double> hubs = hitsBolt.getHubs();

        assertTrue("Link from parent to child should exist", links.get(fromUrl).contains(toUrl));
        assertEquals("Initial authority score should be set", 1.0, authorities.get(fromUrl), 0.001);
        assertEquals("Initial authority score should be set", 1.0, authorities.get(toUrl), 0.001);
        assertEquals("Initial hub score should be set", 1.0, hubs.get(fromUrl), 0.001);
        assertEquals("Initial hub score should be set", 1.0, hubs.get(toUrl), 0.001);

        hitsBolt.addLinkToGraph(fromUrl, secondToUrl);

        links = hitsBolt.getLinks();
        authorities = hitsBolt.getAuthorities();
        hubs = hitsBolt.getHubs();

        assertTrue(
                "Link from parent to child should exist", links.get(fromUrl).contains(secondToUrl));
        assertEquals("Initial hub score should be set", 1.0, hubs.get(secondToUrl), 0.001);
        assertEquals(
                "Initial authority score should be set", 1.0, authorities.get(secondToUrl), 0.001);
    }

    @Test
    public void testHubAndAuthorityScores() {
        String url1 = "http://example.com/page1";
        String url2 = "http://example.com/page2";
        String url3 = "http://example.com/page3";
        String url4 = "http://example.com/page4";
        String url5 = "http://example.com/page5";
        String url6 = "http://example.com/page6";

        hitsBolt.addLinkToGraph(url1, url2);
        hitsBolt.addLinkToGraph(url1, url3);
        hitsBolt.addLinkToGraph(url2, url4);
        hitsBolt.addLinkToGraph(url2, url5);
        hitsBolt.addLinkToGraph(url3, url6);
        hitsBolt.addLinkToGraph(url4, url1);
        hitsBolt.addLinkToGraph(url4, url5);
        hitsBolt.addLinkToGraph(url4, url6);
        hitsBolt.addLinkToGraph(url5, url1);
        hitsBolt.addLinkToGraph(url6, url2);

        hitsBolt.performHITSTest(5);

        Map<String, Double> authorities = hitsBolt.getAuthorities();
        Map<String, Double> hubs = hitsBolt.getHubs();

        assertEquals("Authority score should be set", 0.526, authorities.get(url1), 0.01);
        assertEquals("Authority score should be set", 0.207, authorities.get(url2), 0.01);
        assertEquals("Authority score should be set", 0.127, authorities.get(url3), 0.01);
        assertEquals("Authority score should be set", 0.191, authorities.get(url4), 0.01);
        assertEquals("Authority score should be set", 0.590, authorities.get(url5), 0.01);
        assertEquals("Authority score should be set", 0.526, authorities.get(url6), 0.01);

        assertEquals("Authority score should be set", 0.194, hubs.get(url1), 0.01);
        assertEquals("Authority score should be set", 0.404, hubs.get(url2), 0.01);
        assertEquals("Authority score should be set", 0.254, hubs.get(url3), 0.01);
        assertEquals("Authority score should be set", 0.808, hubs.get(url4), 0.01);
        assertEquals("Authority score should be set", 0.254, hubs.get(url5), 0.01);
        assertEquals("Authority score should be set", 0.119, hubs.get(url6), 0.01);
    }
}
