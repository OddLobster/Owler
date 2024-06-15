package eu.ows.owler.bolt;

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


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        
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

        harvestRate = numRelevantUrls/numTotalUrls;

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

        String[] urlPath = metadata.getValues("url.path");
        
        float processingTimeSeconds = processingTimeMs / 1000.0f;
        float totalProcessingTimeSeconds = totalProcessingTime / 1000.0f;

        LOG.info("EVAL URLPATH: {}", Arrays.toString(urlPath));
        LOG.info("EVAL HARVEST RATE: {}", harvestRate);
        LOG.info("EVAL NUM RELEVANT URLS: {}", numRelevantUrls);
        LOG.info("EVAL NUM TOTAL URLS: {}", numTotalUrls);
        LOG.info("EVAL SUM OF INFORMATION: {}", sumOfInformation);
        LOG.info("EVAL PROCESSING TIME: {} ms ({} s)", processingTimeMs, processingTimeSeconds);
        LOG.info("EVAL TOTAL PROCESSING TIME: {} ms ({} s)", totalProcessingTime, totalProcessingTimeSeconds);


      
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
