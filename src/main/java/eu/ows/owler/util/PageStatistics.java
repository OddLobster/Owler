package eu.ows.owler.util;
import java.util.List;
import java.util.ArrayList;

public class PageStatistics {
    public Integer numSegments;
    public Integer numBlocks;
    public Integer numRelevantPageBlocks;

    public Float wholePageLOFVariance;
    public Float wholePageLOFMean;
    public Float wholePageLOFSum;
    public Float wholePageRelevantBlockPercentage;

    // pageBlock is every block identifies by Boilerpipe
    public List<Float> pageBlockOutlierScores;
    public List<String> pageBlockPredictions;

    // pageSegment is a grouping of pageBlocks specified by NUM_SEGMENTS
    public List<Float> pageSegmentLOFMeans;
    public List<Float> pageSegmentLOFSums;
    public List<Float> pageSegmentLOFVariances;
    public List<Float> pageSegmentRelevantBlockPercentages;
    
    public PageStatistics() {
        pageBlockOutlierScores = new ArrayList<>();
        pageBlockPredictions = new ArrayList<>();
        pageSegmentLOFMeans = new ArrayList<>();
        pageSegmentLOFSums = new ArrayList<>();
        pageSegmentLOFVariances = new ArrayList<>();
        pageSegmentRelevantBlockPercentages = new ArrayList<>();        
    }

    // NOTE
    // add pageBlockLinkDensity?
    // pageSegmentLinkDensity?
    // NumOfWords per block and segment
    // etc...
    // Use this to train additional classifier?
    // Or use decision trees to identify relevant threshold cutoffs
}
