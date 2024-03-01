package eu.ows.owler.parse.filter;

import static org.junit.Assert.assertEquals;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DatasetFilterTest {

    private final String mdKey = "isCurlieDomain";
    private final String datasetPath = "curlie.txt";

    private DatasetFilter urlDatasetFilter;
    private DatasetFilter domainDatasetFilter;

    @Before
    public void setUpContext() {
        final Map<String, Object> configuration = new HashMap<>();
        final Map<String, Object> filterParams = new HashMap<>();
        filterParams.put("key", mdKey);
        filterParams.put("datasetPath", datasetPath);
        filterParams.put("bloomFilterSize", 2275150);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode filterParamsNode = mapper.valueToTree(filterParams);

        domainDatasetFilter = new DatasetFilter();
        domainDatasetFilter.configure(configuration, filterParamsNode);

        filterParams.put("compareMode", "url");
        mapper = new ObjectMapper();
        filterParamsNode = mapper.valueToTree(filterParams);

        urlDatasetFilter = new DatasetFilter();
        urlDatasetFilter.configure(configuration, filterParamsNode);
    }

    @Test
    public void testDomainAccuracy() {
        final List<String> urls = new ArrayList<>();
        urls.add("http://gymnazium-kadan.cz");
        urls.add("http://www.albanywoodworks.com");
        urls.add("https://stormcrawler.net");
        urls.add("http://albanywoodworks.com");

        final List<String> results = new ArrayList<>();
        final List<String> expectedResults = List.of("false", "true", "false", "true");
        final ParseResult parse = new ParseResult();
        for (String url : urls) {
            final ParseData parseData = parse.get(url);
            final Metadata metadata = new Metadata();
            parseData.setMetadata(metadata);

            domainDatasetFilter.filter(url, null, null, parse);

            results.add(parseData.getMetadata().getFirstValue(mdKey));
        }

        assertEquals(results, expectedResults);
    }

    @Test
    public void testUrlAccuracy() {
        final List<String> urls = new ArrayList<>();
        urls.add("http://gymnazium-kadan.cz");
        urls.add("http://albanywoodworks.com");
        urls.add("https://stormcrawler.net");
        urls.add("http://www.albanywoodworks.com");

        final List<String> results = new ArrayList<>();
        final List<String> expectedResults = List.of("false", "false", "false", "true");
        final ParseResult parse = new ParseResult();
        for (String url : urls) {
            final ParseData parseData = parse.get(url);
            final Metadata metadata = new Metadata();
            parseData.setMetadata(metadata);

            urlDatasetFilter.filter(url, null, null, parse);

            results.add(parseData.getMetadata().getFirstValue(mdKey));
        }
        assertEquals(results, expectedResults);
    }
}
