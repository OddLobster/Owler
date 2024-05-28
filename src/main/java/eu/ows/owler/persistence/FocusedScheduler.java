package eu.ows.owler.persistence;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Scheduler;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import eu.ows.owler.util.CustomScore;
import java.util.*;

/**
 * In this scheduler, we use the default scheduler with modification on case of fetch status code. When focused crawling we dont want to recrawl a URL if it was already processed. 
 * We apply our quality evaluation directly on the defaultFetchInterval.
 */
public class FocusedScheduler extends Scheduler {

    // fetch intervals in minutes
    public int defaultFetchInterval;
    public int fetchErrorFetchInterval;
    public int errorFetchInterval;
    private HashMap<String, Float> scores;
    private static final String AS_IS_NEXTFETCHDATE_METADATA = "status.store.as.is.with.nextfetchdate";

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
            // in case of a focused crawl we dont want to recrawl a url if the already processed it
            case FETCHED:
                minutesIncrement = -1;
                break;
            case DISCOVERED:
                AS_IS_NEXTFETCHDATE_METADATA
            
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