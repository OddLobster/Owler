package eu.ows.owler.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.DocumentFragment;

public class DeduplicationFilter extends ParseFilter {

    private final Set<String> keys = new HashSet<>();

    @Override
    public void configure(Map<String, Object> stormConf, JsonNode filterParams) {
        final JsonNode node = filterParams.get("keys");
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            Iterator<JsonNode> iter = node.iterator();
            while (iter.hasNext()) {
                keys.add(iter.next().asText());
            }
        } else {
            keys.add(node.asText());
        }
    }

    @Override
    public void filter(String URL, byte[] content, DocumentFragment doc, ParseResult parse) {
        final Metadata metadata = parse.get(URL).getMetadata();
        for (final String key : keys) {
            final String[] values = metadata.getValues(key);
            if (values != null && values.length > 1) {
                final Set<String> uniqueValues = new HashSet<>();
                for (final String value : values) {
                    uniqueValues.add(value);
                }
                metadata.setValues(key, uniqueValues.toArray(new String[uniqueValues.size()]));
            }
        }
    }
}
