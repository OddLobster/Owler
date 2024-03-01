package eu.ows.owler.warc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.warc.WARCHdfsBolt;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.storm.metric.api.IMetric;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestWARCConnection {

    public static TopologyContext getMockedTopologyContext() {
        TopologyContext context = mock(TopologyContext.class);
        when(context.registerMetric(anyString(), any(IMetric.class), anyInt()))
                .thenAnswer(
                        new Answer<IMetric>() {

                            @Override
                            public IMetric answer(InvocationOnMock invocation) throws Throwable {
                                return invocation.getArgument(1, IMetric.class);
                            }
                        });
        return context;
    }

    public static Tuple getMockedTestTuple(String url, String content, Metadata metadata) {
        Tuple tuple = mock(Tuple.class);
        when(tuple.getStringByField("url")).thenReturn(url);
        when(tuple.getBinaryByField("content"))
                .thenReturn(content.getBytes(Charset.defaultCharset()));
        if (metadata == null) {
            when(tuple.contains("metadata")).thenReturn(Boolean.FALSE);
        } else {
            when(tuple.contains("metadata")).thenReturn(Boolean.TRUE);
            when(tuple.getValueByField("metadata")).thenReturn(metadata);
        }
        return tuple;
    }

    public static void main(String[] args) {
        WARCHdfsBolt warcHdfsBolt = new WARCHdfsBolt();
        Map<String, Object> configuration = new HashMap<>();
        TopologyContext topologyContext = getMockedTopologyContext();
        warcHdfsBolt.prepare(configuration, topologyContext, null);

        String url = "http://www.example.com";
        String content = "This is a test";
        Metadata metadata = new Metadata();
        Tuple tuple = getMockedTestTuple(url, content, metadata);

        warcHdfsBolt.execute(tuple);
    }
}
