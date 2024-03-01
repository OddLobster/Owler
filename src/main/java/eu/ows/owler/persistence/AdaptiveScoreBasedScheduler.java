package eu.ows.owler.persistence;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.AdaptiveScheduler;
import com.digitalpebble.stormcrawler.persistence.Status;
import eu.ows.owler.util.CustomScore;

import java.util.*;

/**
 * In this scheduler, we use the adaptive scheduler with modification on case of fetch status code.
 * First we extract the number of increased minutes by comparing
 * the provided value from adaptive class with current time, then we apply our quality evaluation.
 */
public class AdaptiveScoreBasedScheduler extends AdaptiveScheduler {

    private HashMap<String, Float> scores;

    @Override
    public void init(Map<String, Object> stormConf) {
        super.init(stormConf);
        scores = CustomScore.getScores(stormConf);
    }

    @Override
    public Optional<Date> schedule(Status status, Metadata metadata) {
        Optional<Date> op = super.schedule(status, metadata);
        if (status.equals(Status.FETCHED)) {
            op = getUpdatedDelayPeriod(metadata, op);
        }
        return op;
    }
    public Optional<Date> getUpdatedDelayPeriod(Metadata metadata, Optional<Date> date) {
        Optional<Date> op;
        int baseDelayPeriod = CustomScore.getDiffMinNow(date);
        int minutesIncrement = CustomScore.getDelayPeriod(metadata, scores, baseDelayPeriod);
        if (minutesIncrement == -1) {
            op = Optional.empty();
        } else {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, minutesIncrement);
            op = Optional.of(cal.getTime());
        }
        return op;
    }
}
