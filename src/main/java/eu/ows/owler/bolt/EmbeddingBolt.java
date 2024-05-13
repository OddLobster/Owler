package eu.ows.owler.bolt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import org.apache.storm.tuple.Values;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.onnxruntime.runner.OnnxRuntimeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.l3s.boilerpipe.document.TextBlock;

import com.ankit.bert.tokenizerimpl.BertTokenizer;

import com.digitalpebble.stormcrawler.Metadata;

import eu.ows.owler.util.PageData;

public class EmbeddingBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingBolt.class);
    private OnnxRuntimeRunner runner;
    private Map<String, Integer> vocabulary;
    private int max_embedding_length = 512;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        String modelFilename = "/bert/model/bert-base-uncased.onnx";

        this.runner = OnnxRuntimeRunner.builder().modelUri(modelFilename).build();
        
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
        
    }

    private INDArray tokenizeText(String text, int max_length)
    {
        BertTokenizer tokenizer = new BertTokenizer();
        List<String> tokens = tokenizer.tokenize(text);
        List<Integer> token_ids = tokenizer.convert_tokens_to_ids(tokens);
        long[] array = token_ids.stream().mapToLong(Integer::longValue).toArray();
        INDArray tokenized_text = Nd4j.createFromArray(new long[][]{array});
        return tokenized_text.castTo(DataType.INT64);
    }

    private static void writeEmbeddingToFile(INDArray embedding, String fileName) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < embedding.length(); i++) {
                sb.append(embedding.getDouble(i)).append(" ");
            }
            writer.println(sb.toString().trim());
        } catch (IOException e) {
            LOG.info("Failed to write embedding to file {}", e);
            e.printStackTrace();
        }
    }

    private INDArray createAttentionMask(INDArray tokens)
    {
        INDArray attention_mask = Nd4j.create(DataType.INT64, 1, tokens.length());
        for (int i = 0; i < tokens.length(); i++)
        {
            if (tokens.getInt(i) != 0)
            {
                attention_mask.putScalar(new long[]{0, i}, 1);
            }
            else{
                attention_mask.putScalar(new long[]{0, i}, 0);
            }
        }
        return attention_mask.castTo(DataType.INT64);
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
        
        // create embedding for each block
        for (int i = 0; i < blocks.size(); i++)
        {
            String text = blockTexts.get(i);
            INDArray tokenized_text = tokenizeText(text, max_embedding_length);
            INDArray attention_mask = createAttentionMask(tokenized_text);
            
            Map<String, INDArray> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", tokenized_text);
            inputs.put("attention_mask", attention_mask);
            
            Map<String, INDArray> output = this.runner.exec(inputs);
            INDArray value = output.get("output");
            INDArray meanEmbedding = value.mean(1);
            double[] embedding = meanEmbedding.data().asDouble();
            // writeEmbeddingToFile(embedding, "/outdata/dummy_embedding_"+i+".txt");
            blockEmbeddings.add(embedding);

        }

        // create embedding for whole web page
        INDArray tokenized_text = tokenizeText(pageText, max_embedding_length);
        tokenized_text = tokenized_text.castTo(DataType.INT64);
        INDArray attention_mask = createAttentionMask(tokenized_text);
        
        Map<String, INDArray> inputs = new LinkedHashMap<>();
        inputs.put("input_ids", tokenized_text);
        inputs.put("attention_mask", attention_mask);
        
        Map<String, INDArray> output = this.runner.exec(inputs);
        INDArray value = output.get("output");
        INDArray meanEmbedding = value.mean(1);
        double[] pageTextEmbedding = meanEmbedding.data().asDouble();
        
        long endTime = System.currentTimeMillis();
        LOG.info("Embedding took: " + (endTime - startTime) + " ms");
        LOG.info("Blocks processed: {}", blocks.size());
        
        pageData.blockEmbeddings = blockEmbeddings;
        pageData.pageTextEmbedding = pageTextEmbedding;
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
