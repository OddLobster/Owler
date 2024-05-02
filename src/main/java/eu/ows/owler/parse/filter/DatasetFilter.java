package eu.ows.owler.parse.filter;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.util.URLPartitioner;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import org.w3c.dom.DocumentFragment;

public class DatasetFilter extends ParseFilter {

    private Cache<String, String> cache;
    private String mdKey = "isCurlieDomain";
    private String datasetPath = "curlie.txt";
    private int setSize = 2275150;
    private String compareMode = "domain";

    @Override
    public void configure(Map<String, Object> stormConf, JsonNode filterParams) {
        JsonNode node = filterParams.get("key");
        if (node != null && node.isTextual()) {
            mdKey = node.asText("isCurlieDomain");
        }

        node = filterParams.get("datasetPath");
        if (node != null && node.isTextual()) {
            datasetPath = node.asText("curlie.txt");
        }

        node = filterParams.get("setSize");
        if (node != null && node.isInt()) {
            setSize = node.asInt(2275150);
        }

        // One of those values [domain, url]
        node = filterParams.get("compareMode");
        if (node != null && node.isTextual()) {
            compareMode = node.asText("domain");
        }
        initCache();
    }

    public void initCache() {
        cache = Caffeine.newBuilder().maximumSize(setSize).build();

        final InputStream inputStream =
                getClass().getClassLoader().getResourceAsStream("dataset/" + datasetPath);
        try (final BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                String key = prepareKey(Metadata.empty, line.trim());
                if (key != null) {
                    cache.put(key, key);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void filter(
            String URL, byte[] bytes, DocumentFragment documentFragment, ParseResult parseResult) {
        filterCache(URL, parseResult);
    }

    public void filterCache(String URL, ParseResult parseResult) {
        final Metadata metadata = parseResult.get(URL).getMetadata();
        String key = prepareKey(metadata, URL);
        boolean isUrlExist = false;
        if (key != null) {
            if (cache.getIfPresent(key) != null) {
                isUrlExist = true;
            }
        }
        metadata.setValue(mdKey, String.valueOf(isUrlExist));
    }

    private String prepareKey(Metadata metadata, String URL) {
        String key = URL;
        if (compareMode.equals("domain")) {
            String domain = metadata.getFirstValue("domain");
            if (domain == null) {
                domain =
                        URLPartitioner.getPartition(
                                URL, Metadata.empty, Constants.PARTITION_MODE_DOMAIN);
            }
            key = domain;
        }
        return key;
    }
}
