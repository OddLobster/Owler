package eu.ows.owler.filtering;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.filtering.URLFilter;
import com.fasterxml.jackson.databind.JsonNode;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandom;
import java.net.URL;
import java.util.Map;

public class EpsilonGreedyFilter extends URLFilter {

    private final XoRoShiRo128PlusPlusRandom random = new XoRoShiRo128PlusPlusRandom();

    private double epsilon = 1.;

    @Override
    public void configure(Map<String, Object> stormConf, JsonNode filterParams) {
        JsonNode node = filterParams.get("epsilon");
        if (node != null && node.isDouble()) {
            epsilon = node.doubleValue();
        }
    }

    @Override
    public String filter(URL sourceUrl, Metadata sourceMetadata, String urlToFilter) {
        final double randomValue = random.nextDouble();
        if (randomValue < epsilon) {
            return urlToFilter;
        } else {
            return null;
        }
    }
}
