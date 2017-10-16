package io.primeval.codex.promise;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

public class PromiseHelperTest {

	@Test
	public void mapFallible() throws InterruptedException {

		Promise<Integer> promise = PromiseHelper.mapFallible(Promises.resolved("foo"), s -> {
			if ("foo".equals(s)) {
				throw new Exception();
			}
			return 32;
		});
		Assertions.assertThat(promise.getFailure().getClass()).isSameAs(Exception.class);
	}

}
