package eu.ows.owler.bolt;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.Outlink;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.MetadataTransfer;

import eu.ows.owler.util.PageData;
import eu.ows.owler.util.URLCache;

public class ClassificationBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationBolt.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;
    private Random rand;
    private static final String AS_IS_NEXTFETCHDATE_METADATA = "status.store.as.is.with.nextfetchdate";
    private MetadataTransfer mdTransfer;
    private URLCache urlCache;

    private static final double MIN_RELEVANCE = -1.0;
    private static final double MAX_RELEVANCE = 1.0;
    private static final int MAX_SECONDS = 1000000;
    private static final double PARENT_INFLUENCE_FACTOR = 0.3;
    private int highest_lof_score = 0;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
        declarer.declareStream(StatusStreamName, new Fields("url", "metadata", "status"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        mdTransfer = MetadataTransfer.getInstance(stormConf);
        String redisHost = ConfUtils.getString(stormConf, "redis.host", "frue_ra_redis");
        int redisPort = ConfUtils.getInt(stormConf, "redis.port", 6379);
        urlCache = new URLCache(redisHost, redisPort);
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


        for (int i = 0; i < pageData.blockLinks.size(); i++)
        {
            for (int j = 0; j < pageData.blockLinks.get(i).size(); j++)
            {   
                String childUrl = pageData.blockLinks.get(i).get(j);
                double pageBlockLinkRelevance = (pageData.pageRelevance * PARENT_INFLUENCE_FACTOR) + pageData.pageStats.pageBlockOutlierScores.get(i);
                LOG.info("PageBlockRelevance: {} | PageBlockLinkRelevance: {} | link: {}", pageData.pageStats.pageBlockOutlierScores.get(i), pageBlockLinkRelevance, childUrl);
                long mappedSeconds = Math.round((1 + (MAX_SECONDS - 1) * (1 - (pageBlockLinkRelevance - MIN_RELEVANCE) / (MAX_RELEVANCE - MIN_RELEVANCE))));

                Metadata newMetadata = new Metadata();
                try
                {
                    newMetadata = mdTransfer.getMetaForOutlink(childUrl, new URL(url).toExternalForm(), metadata); 
                }
                catch (MalformedURLException e)
                {
                    LOG.info("MALFORMED URL EXCEPTION ? {}", e);
                }
                
                // page tunneling
                // if (!pageData.isRelevant)
                // {
                //     if (newMetadata.containsKey("maxPageLinkDepth"))
                //     {
                //         int maxPageLinkDepth = Integer.valueOf(newMetadata.getFirstValue("maxPageLinkDepth"));
                //         maxPageLinkDepth -= 1;
                //         if (maxPageLinkDepth == -1)
                //         {
                //             LOG.info("EXCLUDING URL {}. MAX IRRELEVANT PAGE DEPTH REACHED", url);
                //             newMetadata.remove(AS_IS_NEXTFETCHDATE_METADATA);
                //             newMetadata.setValue("maxPageLinkDepth", Integer.toString(maxPageLinkDepth));     
                //             collector.emit(StatusStreamName, input, new Values(url, newMetadata, Status.FETCHED));   
                //             collector.ack(input);
                //             break;
                //         }
                //         newMetadata.setValue("maxPageLinkDepth", Integer.toString(maxPageLinkDepth));   
                //     }
                // }

                //TODO rethink this approach, does it even make sense to give non relevant urls a chance as the runtime is so high? 
                // could do a comparison 

                // block tunneling
                // if (newMetadata.containsKey("maxLinkDepth"))
                // {
                //     if (pageData.pageBlockRelevance.get(i) == false)
                //     {
                //         // decrement maxLinkDepth
                //         int linkDepth = Integer.valueOf(newMetadata.getFirstValue("maxLinkDepth"));
                //         linkDepth -= 1;

                //         if (linkDepth == -1)
                //         {
                //             LOG.info("EXCLUDING URL {}. MAX IRRELEVANT DEPTH REACHED", url);
                //             newMetadata.remove(AS_IS_NEXTFETCHDATE_METADATA);
                //             newMetadata.setValue("maxLinkDepth", Integer.toString(linkDepth));     
                //             collector.emit(StatusStreamName, input, new Values(url, newMetadata, Status.FETCHED));   
                //             collector.ack(input);
                //             continue;
                //         }
                //         newMetadata.setValue("maxLinkDepth", Integer.toString(linkDepth));     
                //     }
                // }
                
                if (childUrl.equals(url))
                {
                    continue;
                }

                Instant timeNow = Instant.ofEpochSecond(mappedSeconds);
                String nextFetchDate = DateTimeFormatter.ISO_INSTANT.format(timeNow);
                newMetadata.setValue(AS_IS_NEXTFETCHDATE_METADATA, nextFetchDate);
                if (urlCache.isUrlCrawled(childUrl) == false)
                {
                    Outlink outlink = new Outlink(childUrl);
                    outlink.setMetadata(newMetadata);
                    collector.emit(StatusStreamName, input, new Values(outlink.getTargetURL(), outlink.getMetadata(), Status.DISCOVERED));
                }
                else{
                    LOG.info("CHILD ALREADY CRAWLED: {}", childUrl);
                }
            }
        }
        long endTime = System.currentTimeMillis();

        LOG.info("ClassificationBolt processing took time {} ms", endTime - startTime);
        pageData.addBoltProcessingTime("classificationBolt", endTime - startTime);
        LOG.info("Metadata for {} is \n{}", url, metadata);

        // refetchable_from_date=0 indicates this url is done with processing and shouldnt be processed in the future
        metadata.remove(AS_IS_NEXTFETCHDATE_METADATA);
        collector.emit(StatusStreamName, input, new Values(url, metadata, Status.FETCHED)); 
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
