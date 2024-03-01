package eu.ows.owler.filtering;

import com.digitalpebble.stormcrawler.Metadata;
import org.junit.Assert;
import org.junit.Test;

public class GzippedSitemapFilterTest {

    @Test
    public void test() {
        final GzippedSitemapFilter filter = new GzippedSitemapFilter();

        final String urlXml = "https://www.example.com/sitemap.xml";
        final String urlXmlGz = "https://www.example.com/sitemap.xml.gz";
        final Metadata metadata = new Metadata();

        String filterResult = filter.filter(null, metadata, urlXml);
        Assert.assertNotNull(filterResult);

        filterResult = filter.filter(null, metadata, urlXmlGz);
        Assert.assertNull(filterResult);

        metadata.addValue("isSitemap", "true");

        filterResult = filter.filter(null, metadata, urlXml);
        Assert.assertNotNull(filterResult);

        filterResult = filter.filter(null, metadata, urlXmlGz);
        Assert.assertNotNull(filterResult);
    }
}
