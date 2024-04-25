package eu.ows.owler.bolt;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.onnxruntime.runner.OnnxRuntimeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        LOG.info("What is this: " + tokens.size());
        INDArray tokenized_text = Nd4j.create(new int[] {max_length});
        LOG.info("IM MAD: " + tokenized_text.length());
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (vocabulary.containsKey(token)) {
                int token_id = vocabulary.get(token);
                tokenized_text.putScalar(i, token_id);
            }
        }
        for (int i = tokens.size(); i < max_length; i++)
        {
            tokenized_text.putScalar(i, 0);
        }
        assert(tokenized_text.length() == max_length);
        return tokenized_text;
    }

    private INDArray createAttentionMask(INDArray tokens)
    {
        INDArray attention_mask = Nd4j.create(new int[] {(int) tokens.length()});
        for (int i = 0; i < tokens.length(); i++)
        {
            if (tokens.getInt(i) != 0)
            {
                attention_mask.putScalar(i, 1);
            }
            else{
                attention_mask.putScalar(i, 0);
            }
        }
        attention_mask = attention_mask.castTo(DataType.INT64);
        return attention_mask;
    }

    @Override
    public void execute(Tuple input) {
        long startTime = System.currentTimeMillis();
        String url = input.getStringByField("url");
        String text = input.getStringByField("text");
        LOG.info("Called EmbeddingBolt");
        LOG.info("Website Text: ", text);
        text = "This is an example web text";
        INDArray tokenized_text = tokenizeText(text, max_embedding_length);
        tokenized_text = tokenized_text.castTo(DataType.INT64);
        INDArray attention_mask = createAttentionMask(tokenized_text);

        String test = "@@@tokenized_text: " + tokenized_text;
        LOG.info(test);
        String test1 = "###attention_mask: " + attention_mask;
        LOG.info(test1);

        Map<String, INDArray> inputs = new LinkedHashMap<>();
        inputs.put("input_ids", tokenized_text);
        inputs.put("attention_mask", attention_mask);

        Map<String, INDArray> output = this.runner.exec(inputs);
        for (Map.Entry<String, INDArray> entry : output.entrySet()) {
            String key = entry.getKey();
            INDArray value = entry.getValue();
            System.out.println("Key: " + key);
            System.out.println("Value: " + value);
        }
        long endTime = System.currentTimeMillis();
        LOG.info("Embedding took: " + (endTime - startTime)/1_000_000 + " ms");
        // this.collector.emit(input, new Values(url, content, metadata, text));
        collector.ack(input);
    }
}
