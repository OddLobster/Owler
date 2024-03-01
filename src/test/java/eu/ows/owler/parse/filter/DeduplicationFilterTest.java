package eu.ows.owler.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DeduplicationFilterTest {

    private DeduplicationFilter deduplicationFilter;

    @Before
    public void setUpContext() {
        final Map<String, Object> configuration = new HashMap<>();
        final Map<String, Object> filterParams = new HashMap<>();
        filterParams.put("keys", "some-random-key");
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode filterParamsNode = mapper.valueToTree(filterParams);

        deduplicationFilter = new DeduplicationFilter();
        deduplicationFilter.configure(configuration, filterParamsNode);
    }

    @Test
    public void test() {
        final Metadata metadata = new Metadata();
        metadata.addValue("some-random-key", "value1");
        metadata.addValue("some-random-key", "value2");
        metadata.addValue("some-random-key", "value1");

        final ParseResult parse = new ParseResult();
        final ParseData parseData = parse.get("http://example.com");
        parseData.setMetadata(metadata);

        deduplicationFilter.filter("http://example.com", null, null, parse);

        Assert.assertEquals(2, parseData.getMetadata().getValues("some-random-key").length);
    }
}
