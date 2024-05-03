package eu.ows.owler.bolt;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class LOFBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(LOFBolt.class);
    private CloseableHttpClient httpClient;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    public void cleanup() {
        try {
            this.httpClient.close();
        } catch (Exception e) {
            LOG.error("Error closing HttpClient", e);
        }
    }

    @Override
    public void execute(Tuple input) {
        long startTime = System.currentTimeMillis();
        double[] embedding = (double[]) input.getValueByField("embedding");

        try {
            // Convert embedding to JSON
            JSONObject json = new JSONObject();
            json.put("embedding", embedding);

            // Send POST request
            HttpPost post = new HttpPost("http://lof-service:43044/predict");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(json.toString(), StandardCharsets.UTF_8));

            CloseableHttpResponse response = this.httpClient.execute(post);
            String result = EntityUtils.toString(response.getEntity());
            JSONObject responseJson = new JSONObject(result);
            String prediction = responseJson.getString("prediction");
            LOG.info("Prediction: {}", prediction);

            response.close();
        } catch (Exception e) {
            LOG.error("Failed prediction", e);
        }

        long endTime = System.currentTimeMillis();
        LOG.info("Time: {}", endTime - startTime);
        collector.ack(input);
    }
}
