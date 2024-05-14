package eu.ows.owler.bolt;

import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;
import org.apache.storm.tuple.Values;
import eu.ows.owler.util.PageData;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.Outlink;

public class ClassificationBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationBolt.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;
    private Random rand;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
        declarer.declareStream(StatusStreamName, new Fields("url", "metadata", "status"));

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

        pageData.isRelevant = pageIsRelevant;

        if (pageIsRelevant) {
            List<String> outlinksList = new ArrayList<>();
            for (int i = 0; i < pageData.blockLinks.size(); i++)
            {
                if (pageData.pageBlockRelevance.get(i) == false)
                {
                    continue;
                }
                for (int j = 0; j < pageData.blockLinks.get(i).size(); j++)
                {
                    outlinksList.add(pageData.blockLinks.get(i).get(j));
                }
            }

            metadata.setValues("outlinks", outlinksList.toArray(new String[outlinksList.size()]));
        }



        long endTime = System.currentTimeMillis();

        LOG.info("ClassificationBolt processing took time {} ms", endTime - startTime);
        LOG.info("Metadata for {} is {}", url, metadata);
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
