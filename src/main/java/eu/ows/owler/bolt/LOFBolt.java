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
import org.apache.storm.tuple.Values;
import com.digitalpebble.stormcrawler.Metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.ows.owler.util.PageData;

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
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
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
        PageData pageData = (PageData) input.getValueByField("pageData"); 
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");


        double[] pageEmbedding = pageData.pageTextEmbedding;
        List<List<String>> blockLinks = pageData.blockLinks;
        List<String> pageTextBlocks = pageData.blockTexts;
        List<double[]> pageBlockEmbeddings = pageData.blockEmbeddings;

        List<String> predictions = new ArrayList<>();
        List<Float> outlierScores = new ArrayList<>();
        
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
                String lof_score = responseJson.getString("lof_score");
                outlierScores.add(Float.parseFloat(lof_score));
                predictions.add(prediction);
                response.close();
                
            } catch (Exception e) {
                LOG.error("Failed prediction", e);
            }
        }
        pageData.pageStats.pageBlockOutlierScores = outlierScores;
        pageData.pageStats.pageBlockPredictions = predictions;

        double RELEVANT_THRESHOLD = 0.2;
        int NUM_SEGMENTS = 4;
        List<Boolean> pageBlockRelevance = new ArrayList<>();

        int numRelevantBlocks = 0;
        double totalRelevantLOFScore = 0;
        for (int i = 0; i < predictions.size(); i++) 
        {
            if (predictions.get(i).equals("1")) {
                numRelevantBlocks += 1;
                pageBlockRelevance.add(true);
                totalRelevantLOFScore += outlierScores.get(i);
            }
            else{
                pageBlockRelevance.add(false);
            }
        }
        pageData.pageBlockRelevance = pageBlockRelevance;


        // NOTE this is only temporary. Need to figure out proper classification. (Decision tree thresholds??????)
        Boolean pageIsRelevant = ((float)numRelevantBlocks/predictions.size()) > RELEVANT_THRESHOLD ? true : false;


        Float wholePageLOFVariance = 0.0f;
        Float wholePageLOFMean = 0.0f;
        Float wholePageLOFSum = 0.0f;
        Float wholePageRelevantBlockPercentage = 0.0f;

        List<Float> pageSegmentLOFMeans = new ArrayList<>();
        List<Float> pageSegmentLOFSums = new ArrayList<>();
        List<Float> pageSegmentLOFVariances = new ArrayList<>();
        List<Float> pageSegmentRelevantBlockPercentages = new ArrayList<>();        

        if (predictions.size() > 0)
        {
                    
            int segmentSize = outlierScores.size() / NUM_SEGMENTS;
            for (int i = 0; i < NUM_SEGMENTS; i++) {
                int start = i * segmentSize;
                int end = start + segmentSize;
                List<Float> segment = new ArrayList<>(outlierScores.subList(start, end));
                List<String> segmentPredictions = new ArrayList<>(predictions.subList(start, end));
                int numRelevantBlocksPerSegment = 0;
                for (int j = 0; j < segmentPredictions.size(); j++)
                {
                    if(segmentPredictions.get(j).equals("1"))
                    {
                        numRelevantBlocksPerSegment += 1;
                    }
                }
                float percentageRelevantBlocksInSegment = (float)numRelevantBlocksPerSegment/(float)segmentPredictions.size();
                float sum = 0;
                for (Float score : segment) {
                    sum += score;
                }
                float mean = sum/segment.size();
                float sumOfSquares = 0;
                for (Float score : segment) {
                    sumOfSquares += Math.pow(score - mean, 2);
                }
                float variance = sumOfSquares / segment.size();
                pageSegmentLOFMeans.add(mean);
                pageSegmentLOFSums.add(sum);
                pageSegmentLOFVariances.add(variance);
                pageSegmentRelevantBlockPercentages.add(percentageRelevantBlocksInSegment);
            }

            float sum = 0;
            for (Float score : outlierScores) {
                sum += score;
            }
            float mean = sum/outlierScores.size();
            float sumOfSquares = 0;
            for (Float score : outlierScores) {
                sumOfSquares += Math.pow(score - mean, 2);
            }
            float variance = sumOfSquares / outlierScores.size();
            wholePageLOFMean = mean;
            wholePageLOFSum = sum;
            wholePageLOFVariance = variance;
            wholePageRelevantBlockPercentage = (float)numRelevantBlocks/(float)predictions.size();
        }
        LOG.info("{} relevant blocks in url: {}", Integer.toString(numRelevantBlocks), url);
        long endTime = System.currentTimeMillis();
        LOG.info("LOFBolt processing time: {} ms", endTime - startTime);


        pageData.pageStats.numSegments = NUM_SEGMENTS;
        pageData.pageStats.numBlocks = pageTextBlocks.size();
        pageData.pageStats.numRelevantPageBlocks = numRelevantBlocks;
        pageData.pageStats.pageSegmentLOFMeans = pageSegmentLOFMeans;
        pageData.pageStats.pageSegmentLOFSums = pageSegmentLOFSums;
        pageData.pageStats.pageSegmentLOFVariances = pageSegmentLOFVariances;
        pageData.pageStats.pageSegmentRelevantBlockPercentages = pageSegmentRelevantBlockPercentages;
        pageData.pageStats.wholePageLOFMean = wholePageLOFMean;
        pageData.pageStats.wholePageLOFSum = wholePageLOFSum;
        pageData.pageStats.wholePageLOFVariance = wholePageLOFVariance;
        pageData.pageStats.wholePageRelevantBlockPercentage = wholePageRelevantBlockPercentage;
        pageData.pageRelevance = totalRelevantLOFScore;

        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);











        // NOTE - Just for testing purposes, remove later!
        String filename = OUTPUT_FOLDER + "failed.txt";
        String[] parts = url.split("/");
        String lastPart = parts[parts.length - 1];
        try (Stream<Path> filesStream = Files.list(Paths.get(OUTPUT_FOLDER))) {
            int numFiles = (int) filesStream.filter(Files::isRegularFile).count();
            
            filename = OUTPUT_FOLDER + "document_" + (numFiles+1) + ".txt";
        } catch (Exception e)
        {
            LOG.info("Failed to get number of files in Folder {}: {}", OUTPUT_FOLDER, e);
        }
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
        String pageOutlierfactor = responseJson.getString("lof_score");

        try 
        {
            if (false)
            {
                throw new IOException();
            }
            PrintWriter writer = new PrintWriter(new FileWriter(filename.replace(".txt", "") + "_" + pageIsRelevant.toString() + "_"+ lastPart +".txt"));
            writer.println("Whole Page Prediction: " + pagePrediction + "; score: " + pageOutlierfactor + "; URL: "+url);
            for (int i = 0; i < predictions.size(); i++)
            {
                String prediction = predictions.get(i);
                String text = pageTextBlocks.get(i);
                Float score = outlierScores.get(i);
                writer.println(prediction + ": " + "score: " + Float.toString(score)  + " " + text.replace("\n", ""));
            }
            for (int i = 0; i < NUM_SEGMENTS; i++) {
                writer.println("Percentage of relevant blocks segment_" +Integer.toString(i)+ ": " + Float.toString(pageSegmentRelevantBlockPercentages.get(i)));
                writer.println("Sum of all segment_" +Integer.toString(i)+ "(" + Integer.toString(pageSegmentLOFMeans.size()) + ")" + " scores: " + Float.toString(pageSegmentLOFSums.get(i)));
                writer.println("Average of segment_" +Integer.toString(i)+ "(" + Integer.toString(pageSegmentLOFMeans.size()) + ")" + " scores: " + Float.toString(pageSegmentLOFMeans.get(i)));
                writer.println("Variance of segment_"+Integer.toString(i)+ "(" + Integer.toString(pageSegmentLOFMeans.size()) + ")" + " scores: " + Float.toString(pageSegmentLOFVariances.get(i)));
            }
            writer.println("-----Whole Page Stats-----");
            writer.println("Relevant Blocks in page: "+ Integer.toString(numRelevantBlocks));
            writer.println("Percentage of relevant blocks: " + Float.toString((float)numRelevantBlocks/(float)predictions.size()));
            writer.println("Sum of all scores: " + Float.toString(wholePageLOFSum));
            writer.println("Average of scores: " + Float.toString(wholePageLOFMean));
            writer.println("Variance of scores: " + Float.toString(wholePageLOFVariance));

            writer.close();
        }
        catch (IOException e) 
        {
            LOG.info("Failed to write prediction debug info to file {}", e);
            e.printStackTrace();
        }
        // end of debug block

    }
}
