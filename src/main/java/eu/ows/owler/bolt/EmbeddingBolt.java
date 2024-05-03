package eu.ows.owler.bolt;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.bytedeco.dnnl.memory.data_type;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.onnxruntime.runner.OnnxRuntimeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.l3s.boilerpipe.document.TextBlock;

import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.sax.HTMLDocument;
import de.l3s.boilerpipe.sax.HTMLFetcher;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;

public class EmbeddingBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingBolt.class);
    private OnnxRuntimeRunner runner;
    private Map<String, Integer> vocabulary;
    private int max_embedding_length = 512;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
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

    private INDArray tokenizeText(String text, int max_length) {
        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
        Tokenizer tokenizer = tokenizerFactory.create(text.toLowerCase());
        List<String> tokens = tokenizer.getTokens();
        INDArray tokenized_text = Nd4j.create(1, max_length);
        for (int i = 0; i < tokens.size(); i++) {
            if (i >= max_length)
            {
                break;
            }
            String token = tokens.get(i);
            if (vocabulary.containsKey(token)) {
                int token_id = vocabulary.get(token);
                tokenized_text.putScalar(new int[]{0, i}, token_id);
            }
        }
        for (int i = tokens.size(); i < max_length; i++)
        {
            tokenized_text.putScalar(i, 0);
        }
        assert(tokenized_text.length() == max_length);
        return tokenized_text;
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
        INDArray attention_mask = Nd4j.create(1, tokens.length());
        for (int i = 0; i < tokens.length(); i++)
        {
            if (tokens.getInt(i) != 0)
            {
                attention_mask.putScalar(new int[]{0, i}, 1);
            }
            else{
                attention_mask.putScalar(new int[]{0, i}, 0);
            }
        }
        attention_mask = attention_mask.castTo(DataType.INT64);
        return attention_mask;
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
        List<INDArray> blockEmbeddings = new ArrayList<>();
        String text;
        for (int i = 0; i < blocks.size(); i++)
        {
            text = blocks.get(i).getText();
            INDArray tokenized_text = tokenizeText(text, max_embedding_length);
            tokenized_text = tokenized_text.castTo(DataType.INT64);
            INDArray attention_mask = createAttentionMask(tokenized_text);
            
            Map<String, INDArray> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", tokenized_text);
            inputs.put("attention_mask", attention_mask);
            
            Map<String, INDArray> output = this.runner.exec(inputs);
            INDArray value = output.get("output");
            INDArray embedding = value.mean(1);
            writeEmbeddingToFile(embedding, "/outdata/dummy_embedding_"+i+".txt");
            blockEmbeddings.add(embedding);
        }
            
        long endTime = System.currentTimeMillis();
        LOG.info("Embedding took: " + (endTime - startTime) + " ms");
        // this.collector.emit(input, new Values(url, content, metadata, text));
        collector.ack(input);
    }
}
