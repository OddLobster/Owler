package eu.ows.owler.bolt;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.persistence.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class SeleniumFetcherBolt extends BaseRichBolt {
    private static final Logger LOG = LoggerFactory.getLogger(SeleniumFetcherBolt.class);
    private OutputCollector collector;
    private ThreadLocal<WebDriver> threadWebDriver;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;

        threadWebDriver = ThreadLocal.withInitial(() -> {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
            System.setProperty("webdriver.chrome.driver", "/crawler/driver/chromedriver");
            LOG.info("Initializing WebDriver for thread {}", Thread.currentThread().getName());
            return new ChromeDriver(options);
        });
    }

    private WebDriver getWebDriver() {
        return threadWebDriver.get();
    }

    @Override
    public void execute(Tuple tuple) {
        String url = tuple.getStringByField("url");
        Metadata metadata = (Metadata) tuple.getValueByField("metadata");

        LOG.info("Fetching URL: {}", url);
        WebDriver driver = getWebDriver();
        try {
            driver.get(url);
            LOG.info("Successfully accessed URL: {}", url);
            Thread.sleep(2000); 

            String renderedHtml = driver.getPageSource();
            LOG.info("Rendered HTML content length for {}: {}", url, renderedHtml.length());

            byte[] content = renderedHtml.getBytes(StandardCharsets.UTF_8);
            collector.emit(tuple, new Values(url, content, metadata));
            LOG.info("Emitting fetched content for URL: {}", url);
            collector.ack(tuple);
        } catch (Exception e) {
            LOG.error("Error fetching URL: {}", url, e);
            metadata.setValue("fetch.exception", e.getMessage());
            collector.emit(Constants.StatusStreamName, tuple, new Values(url, metadata, Status.FETCH_ERROR));
            collector.ack(tuple);
        }
    }

    @Override
    public void cleanup() {
        WebDriver driver = threadWebDriver.get();
        if (driver != null) {
            LOG.info("Closing WebDriver for thread {}", Thread.currentThread().getName());
            driver.quit();
            threadWebDriver.remove();
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata"));
    }
}
