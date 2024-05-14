package eu.ows.owler.bolt;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;
import org.apache.storm.tuple.Values;
import eu.ows.owler.util.PageData;

import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.sax.HTMLDocument;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;

public class ClassificationBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationBolt.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;
    private Random rand;

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

        long startTime = System.currentTimeMillis();
        String url = input.getStringByField("url");
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");
        PageData pageData = (PageData) input.getValueByField("pageData");


        double WHOLE_PAGE_RELEVANT_THRESHOLD = 0.2;
        double SEGMENT_RELEVANT_THRESHOLD = 0.333;
        int MIN_NUMBER_BLOCKS = 3;
        
        Boolean pageIsRelevant = false;

        // Heuristic rules based on observations (TODO: Programmatic approach with decision trees)
        if (pageData.pageStats.wholePageRelevantBlockPercentage > WHOLE_PAGE_RELEVANT_THRESHOLD && pageData.pageStats.numBlocks > MIN_NUMBER_BLOCKS)
        {
            pageIsRelevant = true;
        }
        for (int i = 0; i < pageData.pageStats.pageSegmentRelevantBlockPercentages.size(); i++)
        {
            if (pageData.pageStats.pageSegmentRelevantBlockPercentages.get(i) > SEGMENT_RELEVANT_THRESHOLD && pageData.pageStats.numBlocks/pageData.pageStats.numSegments >= MIN_NUMBER_BLOCKS)
            {
                pageIsRelevant = true;
            }
        }

        long endTime = System.currentTimeMillis();

        LOG.info("ClassificationBolt processing took time {} ms", endtime - startTime);
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
