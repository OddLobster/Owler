package eu.ows.owler.bolt;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;
import org.apache.storm.tuple.Values;

import de.l3s.boilerpipe.document.TextBlock;
import eu.ows.owler.util.PageData;

import com.digitalpebble.stormcrawler.Metadata;

public class DummyEmbeddingBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(DummyEmbeddingBolt.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;
    private Random rand;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
    }

    private static List<double[]> readEmbeddings(int numEmbeddings) {
        Map<Integer, double[]> embeddings = new LinkedHashMap<>();
        for (int i = 0; i < numEmbeddings; i++) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/outdata/embeddings/dummy_embedding_" + i + ".txt"))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] values = line.split(" ");
                    double[] embeddingArray = new double[values.length];
                    for (int j = 0; j < values.length; j++) {
                        embeddingArray[j] = Double.parseDouble(values[j]);
                    }
                    embeddings.put(i, embeddingArray);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<double[]> embeddingsList = new ArrayList<>();
        for (Map.Entry<Integer, double[]> entry : embeddings.entrySet()) {
            embeddingsList.add(entry.getValue());
        }
        return embeddingsList;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        
        this.vocabulary = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/bert/model/bert_vocabulary.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokenInfo = line.split(" ");
                vocabulary.put(tokenInfo[0], Integer.parseInt(tokenInfo[1]));
            }
            reader.close();
        } catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
        this.embeddings = readEmbeddings(300);
        this.rand = new Random();
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
        LOG.info("Blocks to process: {}", blocks.size());
        int index = this.rand.nextInt(this.embeddings.size());
        double[] randomEmbedding = this.embeddings.get(index);
        List<double[]> embeddings = new ArrayList<>();
        List<String> pageBlockTexts = new ArrayList<>();
        for(int i = 0; i < blocks.size(); i++)
        {
            index = this.rand.nextInt(this.embeddings.size());
            randomEmbedding = this.embeddings.get(index);
            embeddings.add(randomEmbedding);
            pageBlockTexts.add("This is dummy text");
        }
        pageData.pageTextEmbedding = randomEmbedding;
        pageData.blockEmbeddings = embeddings;
        
        long endTime = System.currentTimeMillis();
        LOG.info("Emitted all blocks in: {} ms", endTime-startTime);
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
