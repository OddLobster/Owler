package eu.ows.owler.filtering.regex;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.filtering.URLFilter;
import com.digitalpebble.stormcrawler.filtering.regex.RegexURLFilter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class RegexURLFilterTest {

    private URLFilter createFilter() {
        ObjectNode filterParams = new ObjectNode(JsonNodeFactory.instance);
        filterParams.put("regexFilterFile", "default-regex-filters.txt");
        return createFilter(filterParams);
    }

    private URLFilter createFilter(ObjectNode filterParams) {
        RegexURLFilter filter = new RegexURLFilter();
        Map<String, Object> conf = new HashMap<>();
        filter.configure(conf, filterParams);
        return filter;
    }

    @Test
    public void test() {
        final URLFilter filter = createFilter();

        String url = "ftp://www.someFTP.com/#0";
        Metadata metadata = new Metadata();
        String filterResult = filter.filter(null, metadata, url);
        Assert.assertNull(filterResult);

        url = "https://www.example.com/sitemap.xml";
        filterResult = filter.filter(null, metadata, url);
        Assert.assertNotNull(filterResult);

        url = "https://www.example.com/sitemap/2/en_US/sitemap.xml.gz";
        filterResult = filter.filter(null, metadata, url);
        Assert.assertNotNull(filterResult);

        url = "https://bad.website.com/injection.gz";
        filterResult = filter.filter(null, metadata, url);
        Assert.assertNull(filterResult);
    }
}
