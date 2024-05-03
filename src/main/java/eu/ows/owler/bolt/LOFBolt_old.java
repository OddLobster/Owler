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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.ows.owler.bolt.LOFBridge;
import py4j.GatewayServer;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;

public class LOFBolt_old extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(LOFBolt_old.class);
    private Map<String, Integer> vocabulary;
    private List<double[]> embeddings;
    private LOFBridge lofModel;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        this.lofModel = new LOFBridge();
        this.lofModel.startServer();
    }

    @Override
    public void cleanup() {
        this.lofModel.stopServer();
    }

    @Override
    public void execute(Tuple input) {
        long startTime = System.currentTimeMillis();
        double[] embedding = (double[])input.getValueByField("embedding");

        ////////
        // GatewayServer gateway = new GatewayServer();
        // gateway.start();
        // lof = (LOFInterface) gateway.getPythonServerEntryPoint(new Class[] {LOFInterface.class});
        try
        {
            String prediction = this.lofModel.predict(embedding);
            LOG.info("Prediction ye boi: {}", prediction);
        } catch (Exception e)
        {
            LOG.info("Failed prediction: {}", e);
        }



        long endTime = System.currentTimeMillis();
        LOG.info("Time: {}", endTime-startTime);
        collector.ack(input);
    }
}
