package eu.ows.owler.util;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * This class is the core of quality calculations. It's important to use fields name here in the
 * filters
 */
public class CustomScore {

    public static final QualityScore[] QUALITY_SCORES = {
        new QualityScore("isCurlieDomain", "quality.score.curlie"),
        new QualityScore("isWikipediaWeblinkDomain", "quality.score.wikipediaWeblinks"),
        new QualityScore("isClueWebDomain", "quality.score.clueWeb"),
    };

    public static class QualityScore {
        private String metadataField;
        private String scoreField;

        public QualityScore(String metadataField, String scoreField) {
            this.metadataField = metadataField;
            this.scoreField = scoreField;
        }

        public String getScoreField() {
            return scoreField;
        }

        public String getMetadataField() {
            return metadataField;
        }
    }

    /**
     * @param metadata
     * @param scores list contain score of each quality metric.
     * @param baseDelayPeriod original delay before any modification.
     * @return the new delay period
     *     <p>if original delay is -1 this mean we don't wanna to revisit this url. We assume for
     *     each quality criteria there are a score (-1 to 1) otherwise it's zero
     */
    public static int getDelayPeriod(
            Metadata metadata, HashMap<String, Float> scores, int baseDelayPeriod) {

        if (baseDelayPeriod != -1) {
            float percent = 0;
            for (CustomScore.QualityScore qs : CustomScore.QUALITY_SCORES) {
                if (metadata.containsKey(qs.getMetadataField())) {
                    if (!metadata.getFirstValue(qs.getMetadataField()).equals("false")) {
                        percent += scores.get(qs.getMetadataField());
                    }
                }
            }
            percent = (percent > 1) ? 1 : (percent < -1) ? -1 : percent;
            int minutesIncrement = (int) (baseDelayPeriod + (percent * -1 * baseDelayPeriod));
            return Math.max(minutesIncrement, 0);
        }
        return baseDelayPeriod;
    }

    public static HashMap<String, Float> getScores(Map<String, Object> stormConf) {

        HashMap<String, Float> scores = new HashMap<>();
        for (CustomScore.QualityScore qs : CustomScore.QUALITY_SCORES) {
            scores.put(qs.getMetadataField(), ConfUtils.getFloat(stormConf, qs.getScoreField(), 0));
        }
        return scores;
    }

    public static int getDiffMinNow(Optional<Date> d1) {
        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        return getDiffMin(d1, now);
    }

    public static int getDiffMin(Optional<Date> d1, Date d2) {
        Date expected = d1.get();
        long diff = expected.getTime() - d2.getTime();
        return (int) TimeUnit.MILLISECONDS.toMinutes(diff);
    }
}
