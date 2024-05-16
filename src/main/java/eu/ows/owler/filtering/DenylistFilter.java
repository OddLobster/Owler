package eu.ows.owler.filtering;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.filtering.URLFilter;
import com.fasterxml.jackson.databind.JsonNode;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandom;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.HashSet;
import java.io.FileReader;

public class DenylistFilter extends URLFilter {
    private static final Logger LOG = LoggerFactory.getLogger(DenylistFilter.class);
    private Set<String> urlSet = new HashSet<>();
    private String denylistFile;

    @Override
    public void configure(Map<String, Object> stormConf, JsonNode filterParams) {
        JsonNode node = filterParams.get("denylistFileName");
        if (node != null) {
            denylistFile = node.asText();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(denylistFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2 && !"Science".equalsIgnoreCase(parts[1].trim())) {
                    urlSet.add(parts[0].trim());
                }
            }
        } catch (IOException e) {
            LOG.error("Error reading CSV file", e);
        }
    }

    @Override
    public String filter(URL sourceUrl, Metadata sourceMetadata, String urlToFilter) {
        if (urlSet.contains(urlToFilter)) {
            return null;
        }
        if (sourceMetadata.containsKey("maxLinkDepth"))
        {
            if (Integer.parseInt(sourceMetadata.getFirstValue("maxLinkDepth")) <= -1)
            {
                urlSet.add(urlToFilter);
            }
            return null;
        }
        return urlToFilter;
    }
}
