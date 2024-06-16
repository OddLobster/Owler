package eu.ows.owler.bolt;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
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
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.onnxruntime.runner.OnnxRuntimeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ankit.bert.tokenizerimpl.BertTokenizer;
import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;

import de.l3s.boilerpipe.document.TextBlock;
import eu.ows.owler.util.PageData;
import eu.ows.owler.util.URLCache;

public class EmbeddingBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingBolt.class);
    private OnnxRuntimeRunner runner;
    private URLCache urlCache;
    private static final String AS_IS_NEXTFETCHDATE_METADATA = "status.store.as.is.with.nextfetchdate";
    private int max_embedding_length = 512;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
        declarer.declareStream(StatusStreamName, new Fields("url", "metadata", "status"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        String modelFilename = "/bert/model/bert-base-uncased.onnx";

        this.runner = OnnxRuntimeRunner.builder().modelUri(modelFilename).build();
        
        String redisHost = ConfUtils.getString(stormConf, "redis.host", "frue_ra_redis");
        int redisPort = ConfUtils.getInt(stormConf, "redis.port", 6379);
        urlCache = new URLCache(redisHost, redisPort);

    }

    private INDArray tokenizeText(String text, int max_length)
    {
        BertTokenizer tokenizer = new BertTokenizer();
        List<String> tokens = tokenizer.tokenize(text);
        List<Integer> token_ids = tokenizer.convert_tokens_to_ids(tokens);
        long[] array = token_ids.stream().mapToLong(Integer::longValue).toArray();
        if (array.length < max_length) {
            long[] paddedArray = new long[max_length];
            System.arraycopy(array, 0, paddedArray, 0, array.length);
            array = paddedArray;
        } else if (array.length > max_length) {
            long[] truncatedArray = new long[max_length];
            System.arraycopy(array, 0, truncatedArray, 0, max_length);
            array = truncatedArray;
        }
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

        if(urlCache.isUrlEmbedded(url))
        {
            metadata.remove(AS_IS_NEXTFETCHDATE_METADATA);
            collector.emit(StatusStreamName, input, new Values(url, metadata, Status.FETCHED)); 
            collector.ack(input);        
            return;
        }
        
        urlCache.setUrlAsEmbedded(url);

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
        LOG.info("EmbeddingBolt processing took time {} ms. \n Blocks processed: {}", (endTime - startTime), blocks.size());
        pageData.addBoltProcessingTime("embeddingBolt", endTime - startTime);

        pageData.blockEmbeddings = blockEmbeddings;
        pageData.pageTextEmbedding = pageTextEmbedding;
        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }
}
