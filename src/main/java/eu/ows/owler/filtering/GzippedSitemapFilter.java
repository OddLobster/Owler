package eu.ows.owler.filtering;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.filtering.URLFilter;
import java.net.URL;

/*
 * URLs with the suffix .xml.gz are only allowed if the source URL is a sitemap.
 */
public class GzippedSitemapFilter extends URLFilter {

    @Override
    public String filter(URL sourceUrl, Metadata sourceMetadata, String urlToFilter) {
        if (urlToFilter.endsWith(".xml.gz")) {
            if (sourceMetadata.getFirstValue("isSitemap") == null
                    || !sourceMetadata.getFirstValue("isSitemap").equals("true")) {
                return null;
            }
        }
        return urlToFilter;
    }
}
