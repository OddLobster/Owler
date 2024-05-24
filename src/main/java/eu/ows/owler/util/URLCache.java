package eu.ows.owler.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class URLCache {
    private Jedis jedis;
    private static final Logger LOG = LoggerFactory.getLogger(URLCache.class);

    public URLCache(String host, int port) {
        jedis = new Jedis(host, port);
    }

    public boolean isUrlCrawled(String url) {
        boolean isCrawled = jedis.exists("crawled:" + url);
        LOG.info("Checking URL {} - Crawled: {}", url, isCrawled);
        return isCrawled;
    }

    public boolean setUrlAsCrawled(String url) {
        boolean wasSet = jedis.setnx("crawled:" + url, "true") == 1;
        if (wasSet) {
            jedis.sadd("crawled_urls", url);
            LOG.info("URL {} set as crawled", url);
        } else {
            LOG.info("URL {} was already set as crawled", url);
        }
        return wasSet;
    }

    public boolean isUrlEmbedded(String url) {
        boolean isEmbedded = jedis.exists("embedded:" + url);
        LOG.info("Checking URL {} - Embedded: {}", url, isEmbedded);
        return isEmbedded;
    }

    public boolean setUrlAsEmbedded(String url) {
        boolean wasSet = jedis.setnx("embedded:" + url, "true") == 1;
        if (wasSet) {
            jedis.sadd("embedded_urls", url);
            LOG.info("URL {} set as embedded", url);
        } else {
            LOG.info("URL {} was already set as embedded", url);
        }
        return wasSet;
    }

}
