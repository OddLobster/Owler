package eu.ows.owler.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import org.w3c.dom.DocumentFragment;

public class DiscoveredOnSitemapParseFilter extends ParseFilter {

    private final String mdKey = "isDiscoveredOnSitemap";

    @Override
    public void filter(String URL, byte[] content, DocumentFragment doc, ParseResult parse) {
        final Metadata metadata = parse.get(URL).getMetadata();
        String isDiscoveredOnSitemap = "false";
        if (metadata.containsKey("sitemap.lastModified")) {
            isDiscoveredOnSitemap = "true";
        }
        metadata.setValue(mdKey, isDiscoveredOnSitemap);
    }
}
