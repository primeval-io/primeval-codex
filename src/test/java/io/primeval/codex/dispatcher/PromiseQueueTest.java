package io.primeval.codex.dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.primeval.codex.test.rules.WithCodex;

public class PromiseQueueTest {

    @ClassRule
    public static final WithCodex wCodex = new WithCodex();

    @Rule
    public Timeout globalTimeout = Timeout.seconds(120);

    @Test
    public void test() throws Exception {
        PromiseQueue<Integer> promiseQueue = new PromiseQueue<>(wCodex.getDispatcher()::dispatch, 6, 0);
        List<Promise<Integer>> pmsList = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            int j = i;
            Promise<Integer> promise = promiseQueue.dispatch(() -> {
                long sleepTime = sleepTime();
                Thread.sleep(sleepTime);
                return j;
            });
            pmsList.add(promise);
        }
        Promise<List<Integer>> list = Promises.all(pmsList);

        Assertions.assertThat(list.getValue()).isNotNull();
    }

    private long sleepTime() {
        Random random = new java.util.Random();
        boolean positive = random.nextBoolean();
        int value = random.nextInt(1_000);
        return 1_000L + (positive ? value : -value);
    }

}
