package eu.ows.owler.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.URISyntaxException;


public class URLCache {
    private Jedis jedis;
    private static final Logger LOG = LoggerFactory.getLogger(URLCache.class);

    public static String normalizeUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        URI normalizedUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
        return normalizedUri.toString();
    }

    public URLCache(String host, int port) {
        jedis = new Jedis(host, port);
    }

    public boolean isUrlCrawled(String url) {
        String normalizedUrl = "";
        try
        {
            normalizedUrl = normalizeUrl(url);
        } catch (URISyntaxException e)
        {
            LOG.info("isUrlCrawled URI syntax exception", e);
            return false;
        }

        boolean isCrawled = jedis.exists("crawled:" + normalizedUrl);
        LOG.info("Checking URL {} - Crawled: {} - NORMALIZED: {}", url, isCrawled, normalizedUrl);
        return isCrawled;
    }

    public boolean setUrlAsCrawled(String url) {
        String normalizedUrl = "";
        try 
        {
            normalizedUrl = normalizeUrl(url);
        } catch (URISyntaxException e)
        {
            LOG.info("setUrlAsCrawled URI syntax exception", e);
            return false;
        }

        boolean wasSet = jedis.setnx("crawled:" + normalizedUrl, "true") == 1;
        if (wasSet) {
            jedis.sadd("crawled_urls", normalizedUrl);
            LOG.info("URL {} set as crawled", normalizedUrl);
        } else {
            LOG.info("URL {} was already set as crawled", normalizedUrl);
        }
        return wasSet;
    }

    public boolean isUrlEmbedded(String url) {
        String normalizedUrl = "";
        try
        {
            normalizedUrl = normalizeUrl(url);
        } catch (URISyntaxException e)
        {
            LOG.info("isUrlEmbedded URI syntax exception", e);
            return false;
        }

        boolean isEmbedded = jedis.exists("embedded:" + normalizedUrl);
        LOG.info("Checking URL {} - Embedded: {}", normalizedUrl, isEmbedded);
        return isEmbedded;
    }

    public boolean setUrlAsEmbedded(String url) {
        String normalizedUrl = "";
        try
        {
            normalizedUrl = normalizeUrl(url);
        } catch (URISyntaxException e)
        {
            LOG.info("setUrlAsEmbedded URI syntax exception", e);
            return false;
        }
        
        boolean wasSet = jedis.setnx("embedded:" + normalizedUrl, "true") == 1;
        if (wasSet) {
            jedis.sadd("embedded_urls", normalizedUrl);
            LOG.info("URL {} set as embedded", normalizedUrl);
        } else {
            LOG.info("URL {} was already set as embedded", normalizedUrl);
        }
        return wasSet;
    }

}
