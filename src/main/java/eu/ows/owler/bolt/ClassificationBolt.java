package eu.ows.owler.bolt;

import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

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

import java.net.MalformedURLException;
import java.net.URL;
import com.digitalpebble.stormcrawler.util.MetadataTransfer;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.Outlink;
import com.digitalpebble.stormcrawler.persistence.Status;

public class ClassificationBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationBolt.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;
    private Random rand;
    private static final String AS_IS_NEXTFETCHDATE_METADATA = "status.store.as.is.with.nextfetchdate";
    private MetadataTransfer mdTransfer;

    private static final double MIN_RELEVANCE = -1.0;
    private static final double MAX_RELEVANCE = 1.0;
    private static final int MAX_SECONDS = 1000000;
    private static final double PARENT_INFLUENCE_FACTOR = 0.3;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
        declarer.declareStream(StatusStreamName, new Fields("url", "metadata", "status"));

    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        mdTransfer = MetadataTransfer.getInstance(stormConf);

    }

    @Override
    public void execute(Tuple input) {

        long startTime = System.currentTimeMillis();
        String url = input.getStringByField("url");
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");
        PageData pageData = (PageData) input.getValueByField("pageData");


        Boolean pageIsRelevant = false;

        if (pageData.pageStats.wholePageRelevantBlockPercentage > 0)
        {
            pageIsRelevant = true;
        }
        
        // Heuristic rules based on observations (IDEA: Programmatic approach with decision trees)
        // double WHOLE_PAGE_RELEVANT_THRESHOLD = 0.0;
        // double SEGMENT_RELEVANT_THRESHOLD = 0.333;
        // int MIN_NUMBER_BLOCKS = 3;
        // if (pageData.pageStats.wholePageRelevantBlockPercentage > WHOLE_PAGE_RELEVANT_THRESHOLD && pageData.pageStats.numBlocks > MIN_NUMBER_BLOCKS)
        // {
        //     pageIsRelevant = true;
        // }
        // for (int i = 0; i < pageData.pageStats.pageSegmentRelevantBlockPercentages.size(); i++)
        // {
        //     if (pageData.pageStats.pageSegmentRelevantBlockPercentages.get(i) > SEGMENT_RELEVANT_THRESHOLD && pageData.pageStats.numBlocks/pageData.pageStats.numSegments >= MIN_NUMBER_BLOCKS)
        //     {
        //         pageIsRelevant = true;
        //     }
        // }
        // optional programmatic approach (when annotated data is provided)


        LOG.info("Classificaiton of {} is : {}", url, Boolean.toString(pageIsRelevant));
        pageData.isRelevant = pageIsRelevant;

        if (pageIsRelevant) {
            List<String> outlinksList = new ArrayList<>();
            for (int i = 0; i < pageData.blockLinks.size(); i++)
            {
                if (pageData.pageBlockRelevance.get(i) == false)
                {
                    // continue;
                }

                for (int j = 0; j < pageData.blockLinks.get(i).size(); j++)
                {   
                    String childUrl = pageData.blockLinks.get(i).get(j);
                    outlinksList.add(childUrl);
                    double pageBlockLinkRelevance = (pageData.pageRelevance * PARENT_INFLUENCE_FACTOR) + pageData.pageStats.pageBlockOutlierScores.get(i);
                    long mappedSeconds = Math.round((1 + (MAX_SECONDS - 1) * (1 - (pageBlockLinkRelevance - MIN_RELEVANCE) / (MAX_RELEVANCE - MIN_RELEVANCE))));
                    
                    Instant timeNow = Instant.ofEpochSecond(mappedSeconds);
                    String nextFetchDate = DateTimeFormatter.ISO_INSTANT.format(timeNow);
                    LOG.info("CHILD URL IS RELEVANT: {} | nextFetchDate: {} | mappedSecond: {}", pageBlockLinkRelevance, nextFetchDate, mappedSeconds);
                    Metadata newMetadata = new Metadata();
                    try
                    {
                        newMetadata = mdTransfer.getMetaForOutlink(childUrl, new URL(url).toExternalForm(), metadata); 
                    }
                    catch (MalformedURLException e)
                    {
                        LOG.info("MALFORMED URL EXCEPTION ? {}", e);
                    }
                    
                    if (newMetadata.containsKey("maxLinkDepth"))
                    {
                        if (pageData.pageBlockRelevance.get(i) == false)
                        {
                            // decrement maxLinkDepth
                            int linkDepth = Integer.valueOf(newMetadata.getFirstValue("maxLinkDepth"));
                            linkDepth -= 1;
                            newMetadata.setValue("maxLinkDepth", Integer.toString(linkDepth));     
                        }
                    } 
                    
                    newMetadata.setValue(AS_IS_NEXTFETCHDATE_METADATA, nextFetchDate);

                    Outlink outlink = new Outlink(childUrl);
                    outlink.setMetadata(newMetadata);
                    collector.emit(StatusStreamName, input, new Values(outlink.getTargetURL(), outlink.getMetadata(), Status.DISCOVERED));
                }
            }

            metadata.setValues("outlinks", outlinksList.toArray(new String[outlinksList.size()]));
            LOG.info("OUTLINKS FOR URL: {} |\n {} \n@@@@", url, outlinksList.toString());
        }


        long endTime = System.currentTimeMillis();

        LOG.info("ClassificationBolt processing took time {} ms", endTime - startTime);
        LOG.info("Metadata for {} is {}", url, metadata);
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}