package eu.ows.owler.bolt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.l3s.boilerpipe.document.TextBlock;
import eu.ows.owler.util.PageData;
import eu.ows.owler.util.URLCache;

public class TFIDFEmbeddingBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(TFIDFEmbeddingBolt.class);
    private URLCache urlCache;
    private static final String AS_IS_NEXTFETCHDATE_METADATA = "status.store.as.is.with.nextfetchdate";
    private int max_embedding_length = 512;
    private static final String FLASK_API_URL = "http://localhost:5000/compute_tfidf";

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
        declarer.declareStream(StatusStreamName, new Fields("url", "metadata", "status"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        String redisHost = ConfUtils.getString(stormConf, "redis.host", "frue_ra_redis");
        int redisPort = ConfUtils.getInt(stormConf, "redis.port", 6379);
        urlCache = new URLCache(redisHost, redisPort);
    }

    private List<double[]> computeTFIDF(List<String> texts) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(FLASK_API_URL);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonData = new LinkedHashMap<>();
            jsonData.put("texts", texts);
            String json = mapper.writeValueAsString(jsonData);

            StringEntity entity = new StringEntity(json);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                return mapper.readValue(responseBody, List.class);
            }
        }
    }

    @Override
    public void execute(Tuple input) {
        long startTime = System.currentTimeMillis();
        String url = input.getStringByField("url");
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");
        PageData pageData = (PageData) input.getValueByField("pageData");

        String pageText = pageData.contentText;
        List<String> blockTexts = pageData.blockTexts;
        List<TextBlock> blocks = pageData.contentBlocks;

        LOG.info("Called EmbeddingBolt for {}", url);
        List<double[]> blockEmbeddings = new ArrayList<>();

        if(urlCache.isUrlEmbedded(url)) {
            metadata.remove(AS_IS_NEXTFETCHDATE_METADATA);
            collector.emit(StatusStreamName, input, new Values(url, metadata, Status.FETCHED)); 
            collector.ack(input);        
            return;
        }
        
        urlCache.setUrlAsEmbedded(url);

        try {
            // create embedding for each block
            blockEmbeddings = computeTFIDF(blockTexts);

            // create embedding for whole web page
            List<String> pageTextList = new ArrayList<>();
            pageTextList.add(pageText);
            List<double[]> pageTextEmbeddingList = computeTFIDF(pageTextList);
            double[] pageTextEmbedding = pageTextEmbeddingList.get(0);

            long endTime = System.currentTimeMillis();
            LOG.info("EmbeddingBolt processing took time {} ms. \n Blocks processed: {}", (endTime - startTime), blocks.size());
            pageData.addBoltProcessingTime("embeddingBolt", endTime - startTime);

            pageData.blockEmbeddings = blockEmbeddings;
            pageData.pageTextEmbedding = pageTextEmbedding;
            collector.emit(input, new Values(url, content, metadata, pageData));
            collector.ack(input);
        } catch (IOException e) {
            LOG.error("Error calculating TF-IDF", e);
            collector.fail(input);
        }
    }
}
