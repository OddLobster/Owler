package eu.ows.owler.bolt;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    private static final String OUTPUT_FOLDER = "/outdata/documents/";

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
        String url = input.getStringByField("url");
        double[] pageEmbedding = (double[]) input.getValueByField("pageEmbedding");

        @SuppressWarnings("unchecked")
        List<List<String>> blockLinks = (List<List<String>>) input.getValueByField("blockLinks");

        @SuppressWarnings("unchecked")
        List<String> pageTextBlocks = (List<String>) input.getValueByField("pageTextBlocks");

        @SuppressWarnings("unchecked")
        List<double[]> pageBlockEmbeddings = (List<double[]>) input.getValueByField("pageBlockEmbeddings");

        List<String> predictions = new ArrayList<>();
        
        for (int i = 0; i < pageBlockEmbeddings.size(); i++)
        {
            try {
                String text = pageTextBlocks.get(i);
                double[] embedding = pageBlockEmbeddings.get(i);

                // TODO Temporary calling python api until LOF is implemented in Java
                JSONObject json = new JSONObject();
                json.put("embedding", embedding);
                HttpPost post = new HttpPost("http://lof-service:43044/predict");
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(json.toString(), StandardCharsets.UTF_8));
                CloseableHttpResponse response = this.httpClient.execute(post);
                String result = EntityUtils.toString(response.getEntity());
                JSONObject responseJson = new JSONObject(result);
                String prediction = responseJson.getString("prediction"); 
                predictions.add(prediction);
                response.close();
            } catch (Exception e) {
                LOG.error("Failed prediction", e);
            }
        }

        long endTime = System.currentTimeMillis();
        LOG.info("Time to predict relevance: {}", endTime - startTime);

        //NOTE - Just for testing purposes, remove later!
        // Why does this have to be so convoluted?????
        String filename = OUTPUT_FOLDER + "failed.txt";
        try (Stream<Path> filesStream = Files.list(Paths.get(OUTPUT_FOLDER))) {
            int numFiles = (int) filesStream.filter(Files::isRegularFile).count();
            filename = OUTPUT_FOLDER + "document_" + (numFiles+1) + ".txt";
        } catch (Exception e)
        {
            LOG.info("Failed to get number of files in Folder {}: {}", OUTPUT_FOLDER, e);
        }
        int numRelevantBlocks = 0;
        JSONObject json = new JSONObject();
        json.put("embedding", pageEmbedding);
        HttpPost post = new HttpPost("http://lof-service:43044/predict");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(json.toString(), StandardCharsets.UTF_8));
        String result = "fuck";
        try{
            CloseableHttpResponse response = this.httpClient.execute(post);
            result = EntityUtils.toString(response.getEntity());
        } catch (Exception e) {LOG.info("Failed page Prediction: ", e);}
        JSONObject responseJson = new JSONObject(result);
        String pagePrediction = responseJson.getString("prediction"); 
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename.replace(".txt", "") + (pagePrediction.equals("-1") ? "_0" : "_1") +".txt"))) 
        {
            writer.println("Whole Page Prediction: " + pagePrediction + " URL: "+url);
            for (int i = 0; i < predictions.size(); i++)
            {
                String prediction = predictions.get(i);
                String text = pageTextBlocks.get(i);
                writer.println(prediction + ": " + text.replace("\n", ""));
                if (prediction.equals("1")) {
                    LOG.info("Block {} is relevant in {}", i, url);
                    numRelevantBlocks += 1;
                    for (int j = 0; j < blockLinks.get(i).size(); j++)
                    {
                        LOG.info("  Child Link {} should be prioritzed", blockLinks.get(i).get(j));
                    }
                }
            }
            writer.println("Relevant Blocks in page: "+ Integer.toString(numRelevantBlocks));
            if (predictions.size() > 0)
            {
                writer.println("Percentage of relevant blocks: " + Float.toString((float)numRelevantBlocks/(float)predictions.size()));
            }
            LOG.info("{} relevant blocks in url: {}", Integer.toString(numRelevantBlocks), url);
        } catch (IOException e) 
        {
            LOG.info("Failed to write prediction to file {}", e);
            e.printStackTrace();
        }
        // end of debug block

        collector.ack(input);
    }
}
