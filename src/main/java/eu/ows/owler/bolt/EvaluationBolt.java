package eu.ows.owler.bolt;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.Metadata;

import eu.ows.owler.util.PageData;

public class EvaluationBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(EvaluationBolt.class);
    private int numRelevantUrls = 0;
    private int numTotalUrls = 0;
    private List<String> relevantLinksToFind = new ArrayList<>();
    private float harvestRate = 1;
    private long totalProcessingTime = 0;
    private int numTargetLinks = 0;
    private int numTargetLinksFound = 0;
    private final String LOG_FILE_PATH = "/outdata/stats/RUN_NO_TUNNELING_1wg2.txt";


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        String filePath = "/data/relevantLinksToFind.txt";
        try {
            relevantLinksToFind = Files.readAllLines(Paths.get(filePath));
        } catch (IOException e) {
            LOG.info("Failed to read eval urls {}", e);
        }
        numTargetLinks = relevantLinksToFind.size();
        
    }

    @Override
    public void execute(Tuple input) {
        String url = input.getStringByField("url");
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");
        PageData pageData = (PageData) input.getValueByField("pageData");
        LOG.info("EvaluationBolt for url: {}", url);
        
        float sumOfInformation = 0;
        long processingTimeMs = 0;

        processingTimeMs += Long.parseLong(metadata.getFirstValue("fetch.loadingTime"));
        processingTimeMs += Long.parseLong(metadata.getFirstValue("parse.processingTime"));

        for (float time : pageData.boltProcessingTimes.values()) {
            processingTimeMs += time;
        }

        totalProcessingTime += processingTimeMs;
        numTotalUrls++;
        if (pageData.isRelevant)
        {
            numRelevantUrls++;
        }

        harvestRate = (float)numRelevantUrls/numTotalUrls;

        for (Float lofScore : pageData.pageStats.pageBlockOutlierScores)
        {
            if(lofScore > 0)
            {
                sumOfInformation += lofScore;
            }
        }
        if (!pageData.pageStats.pageBlockOutlierScores.isEmpty())
        {
            sumOfInformation = sumOfInformation/pageData.pageStats.pageBlockOutlierScores.size();
        }

        if (relevantLinksToFind.contains(url))
        {
            numTargetLinksFound += 1;
        }

        String[] urlPath = metadata.getValues("url.path");
        int depth = 0;
        if (urlPath != null)
        {
            depth = urlPath.length;
        }
        float processingTimeSeconds = processingTimeMs / 1000.0f;
        float totalProcessingTimeSeconds = totalProcessingTime / 1000.0f;

        LOG.info("EVAL URLPATH: {}", Arrays.toString(urlPath));
        LOG.info("EVAL PATH DEPTH: {}", depth);
        LOG.info("EVAL HARVEST RATE: {}", harvestRate);
        LOG.info("EVAL NUM RELEVANT URLS: {}", numRelevantUrls);
        LOG.info("EVAL NUM TOTAL URLS: {}", numTotalUrls);
        LOG.info("EVAL SUM OF INFORMATION: {}", sumOfInformation);
        LOG.info("EVAL TARGET RECALL: {}", numTargetLinksFound/numTargetLinks);

        LOG.info("EVAL PROCESSING TIME: {} ms ({} s)", processingTimeMs, processingTimeSeconds);
        LOG.info("EVAL TOTAL PROCESSING TIME: {} ms ({} s)", totalProcessingTime, totalProcessingTimeSeconds);

        try (FileWriter writer = new FileWriter(LOG_FILE_PATH, true)) {
            writer.write("EVAL URL: " + url + "\n");
            writer.write("EVAL URLPATH: " + Arrays.toString(urlPath) + "\n");
            writer.write("EVAL PATH DEPTH: " + depth + "\n");
            writer.write("EVAL HARVEST RATE: " + harvestRate + "\n");
            writer.write("EVAL NUM RELEVANT URLS: " + numRelevantUrls + "\n");
            writer.write("EVAL NUM TOTAL URLS: " + numTotalUrls + "\n");
            writer.write("EVAL SUM OF INFORMATION: " + sumOfInformation + "\n");
            writer.write("EVAL TARGET RECALL: " + String.format("%.2f", (double) numTargetLinksFound / numTargetLinks) + "\n");
            writer.write("EVAL PROCESSING TIME: " + processingTimeMs + " ms (" + processingTimeSeconds + " s)\n");
            writer.write("EVAL TOTAL PROCESSING TIME: " + totalProcessingTime + " ms (" + totalProcessingTimeSeconds + " s)\n");
            writer.write("\n");  // Add a newline for better readability
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
      
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
