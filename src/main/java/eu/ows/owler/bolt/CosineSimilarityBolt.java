package eu.ows.owler.bolt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.ConfUtils;

import eu.ows.owler.util.PageData;

public class CosineSimilarityBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(CosineSimilarityBolt.class);
    private static final String OUTPUT_FOLDER = "/outdata/documents/";
    private float blockRelevanceSensitivity = 0.0f;
    private List<RealVector> referenceCorpusVectors;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        blockRelevanceSensitivity = ConfUtils.getFloat(stormConf, "relevantBlockThreshold", 0.0f);
        loadReferenceCorpus();
    }

    @Override
    public void cleanup() {
    }

    private void loadReferenceCorpus() {
        referenceCorpusVectors = new ArrayList<>();
    }

    @Override
    public void execute(Tuple input) {
        long startTime = System.currentTimeMillis();
        String url = input.getStringByField("url");
        PageData pageData = (PageData) input.getValueByField("pageData"); 
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");

        List<String> pageTextBlocks = pageData.blockTexts;
        List<double[]> pageBlockEmbeddings = pageData.blockEmbeddings;

        List<String> predictions = new ArrayList<>();
        List<Float> outlierScores = new ArrayList<>();
        
        for (int i = 0; i < pageBlockEmbeddings.size(); i++) {
            try {
                double[] embedding = pageBlockEmbeddings.get(i);
                RealVector blockVector = new ArrayRealVector(embedding);
                double maxSimilarity = -1.0;

                for (RealVector referenceVector : referenceCorpusVectors) {
                    double similarity = calculateCosineSimilarity(blockVector, referenceVector);
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity;
                    }
                }

                float adjustedSimilarity = (float) maxSimilarity - blockRelevanceSensitivity;
                outlierScores.add(adjustedSimilarity);
                if (adjustedSimilarity < 0) {
                    predictions.add("-1");
                } else {
                    predictions.add("1");
                }
            } catch (Exception e) {
                LOG.error("Failed to calculate similarity", e);
                outlierScores.add(-1.0f);
                predictions.add("-1");
            }
        }

        pageData.pageStats.pageBlockOutlierScores = outlierScores;
        pageData.pageStats.pageBlockPredictions = predictions;

        double RELEVANT_THRESHOLD = 0.0;
        int NUM_SEGMENTS = 4;
        List<Boolean> pageBlockRelevance = new ArrayList<>();

        int numRelevantBlocks = 0;
        double totalRelevantSimilarity = 0;
        for (int i = 0; i < predictions.size(); i++) {
            if (predictions.get(i).equals("1")) {
                numRelevantBlocks += 1;
                pageBlockRelevance.add(true);
                totalRelevantSimilarity += outlierScores.get(i);
            } else {
                pageBlockRelevance.add(false);
            }
        }
        pageData.pageBlockRelevance = pageBlockRelevance;

        Boolean pageIsRelevant = ((float) numRelevantBlocks / predictions.size()) > RELEVANT_THRESHOLD;

        Float wholePageSimilarityVariance = 0.0f;
        Float wholePageSimilarityMean = 0.0f;
        Float wholePageSimilaritySum = 0.0f;
        Float wholePageRelevantBlockPercentage = 0.0f;

        List<Float> pageSegmentSimilarityMeans = new ArrayList<>();
        List<Float> pageSegmentSimilaritySums = new ArrayList<>();
        List<Float> pageSegmentSimilarityVariances = new ArrayList<>();
        List<Float> pageSegmentRelevantBlockPercentages = new ArrayList<>();        

        if (!predictions.isEmpty()) {
            int segmentSize = outlierScores.size() / NUM_SEGMENTS;
            for (int i = 0; i < NUM_SEGMENTS; i++) {
                int start = i * segmentSize;
                int end = start + segmentSize;
                List<Float> segment = new ArrayList<>(outlierScores.subList(start, end));
                List<String> segmentPredictions = new ArrayList<>(predictions.subList(start, end));
                int numRelevantBlocksPerSegment = 0;
                for (int j = 0; j < segmentPredictions.size(); j++) {
                    if (segmentPredictions.get(j).equals("1")) {
                        numRelevantBlocksPerSegment += 1;
                    }
                }
                float percentageRelevantBlocksInSegment = (float) numRelevantBlocksPerSegment / (float) segmentPredictions.size();
                float sum = 0;
                for (Float score : segment) {
                    sum += score;
                }
                float mean = sum / segment.size();
                float sumOfSquares = 0;
                for (Float score : segment) {
                    sumOfSquares += Math.pow(score - mean, 2);
                }
                float variance = sumOfSquares / segment.size();
                pageSegmentSimilarityMeans.add(mean);
                pageSegmentSimilaritySums.add(sum);
                pageSegmentSimilarityVariances.add(variance);
                pageSegmentRelevantBlockPercentages.add(percentageRelevantBlocksInSegment);
            }

            float sum = 0;
            for (Float score : outlierScores) {
                sum += score;
            }
            float mean = sum / outlierScores.size();
            float sumOfSquares = 0;
            for (Float score : outlierScores) {
                sumOfSquares += Math.pow(score - mean, 2);
            }
            float variance = sumOfSquares / outlierScores.size();
            wholePageSimilarityMean = mean;
            wholePageSimilaritySum = sum;
            wholePageSimilarityVariance = variance;
            wholePageRelevantBlockPercentage = (float) numRelevantBlocks / (float) predictions.size();
        }
        LOG.info("{} relevant blocks in url: {}", Integer.toString(numRelevantBlocks), url);
        long endTime = System.currentTimeMillis();
        LOG.info("CosineSimilarityBolt processing time: {} ms", endTime - startTime);
        pageData.addBoltProcessingTime("CosineSimilarityBolt", endTime - startTime);

        pageData.pageStats.numSegments = NUM_SEGMENTS;
        pageData.pageStats.numBlocks = pageTextBlocks.size();
        pageData.pageStats.numRelevantPageBlocks = numRelevantBlocks;
        pageData.pageStats.pageSegmentLOFMeans = pageSegmentSimilarityMeans;
        pageData.pageStats.pageSegmentLOFSums = pageSegmentSimilaritySums;
        pageData.pageStats.pageSegmentLOFVariances = pageSegmentSimilarityVariances;
        pageData.pageStats.pageSegmentRelevantBlockPercentages = pageSegmentRelevantBlockPercentages;
        pageData.pageStats.wholePageLOFMean = wholePageSimilarityMean;
        pageData.pageStats.wholePageLOFSum = wholePageSimilaritySum;
        pageData.pageStats.wholePageLOFVariance = wholePageSimilarityVariance;
        pageData.pageStats.wholePageRelevantBlockPercentage = wholePageRelevantBlockPercentage;
        pageData.pageRelevance = totalRelevantSimilarity;

        collector.emit(input, new Values(url, content, metadata, pageData));
        collector.ack(input);
    }

    private double calculateCosineSimilarity(RealVector vector1, RealVector vector2) {
        return vector1.dotProduct(vector2) / (vector1.getNorm() * vector2.getNorm());
    }
}
