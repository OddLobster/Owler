package eu.ows.owler.persistence;

import static org.junit.Assert.assertEquals;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import eu.ows.owler.util.CustomScore;
import java.util.*;
import org.junit.Before;
import org.junit.Test;

public class ScoreScheduleTest {

    private Map<String, Object> stormConf;

    @Before
    public void setUpContext() {
        stormConf = new HashMap<>();
        stormConf.put("quality.score.curlie", 0);
        stormConf.put("quality.score.wikipediaWeblinks", -0.5);
        stormConf.put("quality.score.clueWeb", 0.3);
    }

    @Test
    public void getScoreTest() {
        HashMap<String, Float> result = CustomScore.getScores(stormConf);

        assertEquals(result.get("isCurlieDomain"), 0, 0);
        assertEquals(result.get("isWikipediaWeblinkDomain"), -0.5, 0.01);
        assertEquals(result.get("isClueWebDomain"), 0.3, 0.01);
    }

    @Test
    public void getDelayPeriodTest() {
        HashMap<String, Float> scores = CustomScore.getScores(stormConf);
        int noFetch = CustomScore.getDelayPeriod(Metadata.empty, scores, -1);
        assertEquals(noFetch, -1, 0);

        Metadata onlyCurlie = new Metadata();
        onlyCurlie.setValue("isCurlieDomain", "true");
        onlyCurlie.setValue("isWikipediaWeblinkDomain", "false");
        onlyCurlie.setValue("isClueWebDomain", "false");

        int noChange = CustomScore.getDelayPeriod(onlyCurlie, scores, 5);
        assertEquals(noChange, 5, 0);

        Metadata mix = new Metadata();
        mix.setValue("isCurlieDomain", "true");
        mix.setValue("isWikipediaWeblinkDomain", "true");
        mix.setValue("isClueWebDomain", "true");

        int mixChange = CustomScore.getDelayPeriod(mix, scores, 5);
        assertEquals(mixChange, 6, 0);

        mix.setValue("isWikipediaWeblinkDomain", "false");
        mixChange = CustomScore.getDelayPeriod(mix, scores, 10);
        assertEquals(mixChange, 7, 0);
    }

    @Test
    public void scoreBasedSchedulerTest() {
        ScoreBasedScheduler scheduler = new ScoreBasedScheduler();
        scheduler.init(stormConf);
        Metadata mix = new Metadata();
        mix.setValue("isCurlieDomain", "true");
        mix.setValue("isWikipediaWeblinkDomain", "true");
        mix.setValue("isClueWebDomain", "true");
        var op = scheduler.schedule(Status.FETCHED, mix);
        HashMap<String, Float> scores = CustomScore.getScores(stormConf);
        int delayPeriod = CustomScore.getDelayPeriod(mix, scores, scheduler.defaultFetchInterval);
        assertEquals(delayPeriod, CustomScore.getDiffMinNow(op), 2);
        assertEquals(CustomScore.getDiffMinNow(scheduler.schedule(Status.DISCOVERED, mix)), 0, 2);
        assertEquals(
                CustomScore.getDiffMinNow(scheduler.schedule(Status.ERROR, mix)),
                scheduler.errorFetchInterval,
                0);
        assertEquals(
                CustomScore.getDiffMinNow(scheduler.schedule(Status.FETCH_ERROR, mix)),
                scheduler.fetchErrorFetchInterval,
                0);
        assertEquals(
                CustomScore.getDiffMinNow(scheduler.schedule(Status.REDIRECTION, mix)),
                scheduler.defaultFetchInterval,
                0);
    }

    @Test
    public void adaptiveScoreBasedSchedulerTest() {
        AdaptiveScoreBasedScheduler adaptiveScheduler = new AdaptiveScoreBasedScheduler();
        adaptiveScheduler.init(stormConf);
        Metadata mix = new Metadata();
        mix.setValue("isCurlieDomain", "true");
        mix.setValue("isWikipediaWeblinkDomain", "true");
        mix.setValue("isClueWebDomain", "true");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 50);
        Optional<Date> op = Optional.of(cal.getTime());
        Optional<Date> updated = adaptiveScheduler.getUpdatedDelayPeriod(mix, op);
        // assertEquals(CustomScore.getDiffMinNow(updated), 60, 1);
    }
}
