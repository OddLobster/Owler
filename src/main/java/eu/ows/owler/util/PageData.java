package eu.ows.owler.util;

import de.l3s.boilerpipe.document.TextBlock;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import eu.ows.owler.util.PageStatistics;

public class PageData {
    public String url;
    public List<List<String>> blockLinks;        
    public String contentText;                 
    public List<TextBlock> contentBlocks;          
    public List<String> blockTexts;   
    public double[] pageTextEmbedding;            
    public List<double[]> blockEmbeddings;
    public int depth;
    public PageStatistics pageStats;
    public List<Boolean> pageBlockRelevance;
    public Boolean isRelevant;
    public double pageRelevance;
    public Map<String, Long> boltProcessingTimes;
    
    public PageData() {
        blockLinks = new ArrayList<>();
        contentBlocks = new ArrayList<>();
        blockTexts = new ArrayList<>();
        blockEmbeddings = new ArrayList<>();
        pageStats = new PageStatistics();
        boltProcessingTimes = new HashMap<>();
    }

    public void addBoltProcessingTime(String boltName, long timeInMs) {
        boltProcessingTimes.put(boltName, timeInMs);
    }
    
}
