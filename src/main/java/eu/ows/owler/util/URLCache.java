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
        boolean isCrawled = jedis.sismember("crawled_urls", url);
        LOG.info("Checking URL {} - Crawled: {}", url, isCrawled);
        return isCrawled;
    }

    public void setUrlAsCrawled(String url) {
        jedis.sadd("crawled_urls", url);
        LOG.info("URL {} set as crawled", url);
    }
}
