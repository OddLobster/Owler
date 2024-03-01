package eu.ows.owler.persistence;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Scheduler;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import eu.ows.owler.util.CustomScore;

import java.util.*;

/**
 * In this scheduler, we use the default scheduler with modification on case of fetch status code.
 * We apply our quality evaluation directly on the defaultFetchInterval.
 */

public class ScoreBasedScheduler extends Scheduler {

    // fetch intervals in minutes
    public int defaultFetchInterval;
    public int fetchErrorFetchInterval;
    public int errorFetchInterval;
    private HashMap<String, Float> scores;

    @Override
    protected void init(Map<String, Object> stormConf) {
        defaultFetchInterval =
                ConfUtils.getInt(stormConf, Constants.defaultFetchIntervalParamName, 1440);
        fetchErrorFetchInterval =
                ConfUtils.getInt(stormConf, Constants.fetchErrorFetchIntervalParamName, 120);
        errorFetchInterval =
                ConfUtils.getInt(stormConf, Constants.errorFetchIntervalParamName, 44640);
        scores = CustomScore.getScores(stormConf);
    }

    @Override
    public Optional<Date> schedule(Status status, Metadata metadata) {
        int minutesIncrement = 0;

        switch (status) {
            case FETCHED:
                minutesIncrement = CustomScore.getDelayPeriod(metadata, scores, defaultFetchInterval);
                break;
            case FETCH_ERROR:
                minutesIncrement = fetchErrorFetchInterval;
                break;
            case ERROR:
                minutesIncrement = errorFetchInterval;
                break;
            case REDIRECTION:
                minutesIncrement = defaultFetchInterval;
                break;
            default:
                // leave it to now e.g. DISCOVERED
        }
        if (minutesIncrement == -1) {
            return Optional.empty();
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, minutesIncrement);

        return Optional.of(cal.getTime());
    }
}
