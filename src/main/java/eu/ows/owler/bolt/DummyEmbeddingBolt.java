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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.sax.HTMLDocument;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;

public class DummyEmbeddingBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(DummyEmbeddingBolt.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    private static List<double[]> readEmbeddings(int numEmbeddings) {
        Map<Integer, double[]> embeddings = new LinkedHashMap<>();
        for (int i = 0; i < numEmbeddings; i++) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/outdata/embedding_" + i + ".txt"))) {
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
        this.embeddings = readEmbeddings(45);
    }

    @Override
    public void execute(Tuple input) {
        long startTime = System.currentTimeMillis();
        String url = input.getStringByField("url");
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");

        @SuppressWarnings("unchecked")
        List<TextBlock> blocks = (List<TextBlock>) input.getValueByField("blocks");

        LOG.info("Called EmbeddingBolt for {}", url);
        LOG.info("Blocks to process: {}", blocks.size());
        Random rand = new Random();
        int index = rand.nextInt(this.embeddings.size());
        double[] randomEmbedding = this.embeddings.get(index);
        for(int i = 0; i < blocks.size(); i++)
        {
            rand = new Random();
            index = rand.nextInt(this.embeddings.size());
            randomEmbedding = this.embeddings.get(index);
        }
        LOG.info("Final embedding: {}", randomEmbedding);

        long endTime = System.currentTimeMillis();
        LOG.info("Time: {}", endTime-startTime);

        collector.ack(input);
    }
}
