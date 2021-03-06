/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.queryablestate.itcases;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FoldFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.state.AggregatingState;
import org.apache.flink.api.common.state.AggregatingStateDescriptor;
import org.apache.flink.api.common.state.FoldingState;
import org.apache.flink.api.common.state.FoldingStateDescriptor;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.api.common.state.State;
import org.apache.flink.api.common.state.StateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.base.StringSerializer;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.queryablestate.UnknownKeyOrNamespaceException;
import org.apache.flink.queryablestate.client.QueryableStateClient;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.concurrent.ScheduledExecutor;
import org.apache.flink.runtime.concurrent.ScheduledExecutorServiceAdapter;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobStatus;
import org.apache.flink.runtime.messages.JobManagerMessages;
import org.apache.flink.runtime.messages.JobManagerMessages.CancellationSuccess;
import org.apache.flink.runtime.minicluster.FlinkMiniCluster;
import org.apache.flink.runtime.state.AbstractStateBackend;
import org.apache.flink.runtime.state.CheckpointListener;
import org.apache.flink.runtime.state.VoidNamespace;
import org.apache.flink.runtime.state.VoidNamespaceSerializer;
import org.apache.flink.runtime.state.VoidNamespaceTypeInfo;
import org.apache.flink.runtime.testingUtils.TestingJobManagerMessages;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.QueryableStateStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.api.operators.AbstractStreamOperator;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.util.Collector;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.TestLogger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;

import scala.concurrent.Await;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;
import scala.reflect.ClassTag$;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Base class for queryable state integration tests with a configurable state backend.
 */
public abstract class AbstractQueryableStateTestBase extends TestLogger {

	private static final int NO_OF_RETRIES = 100;
	private static final FiniteDuration TEST_TIMEOUT = new FiniteDuration(10000L, TimeUnit.SECONDS);
	private static final Time QUERY_RETRY_DELAY = Time.milliseconds(100L);

	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);
	private final ScheduledExecutor executor = new ScheduledExecutorServiceAdapter(executorService);

	/**
	 * State backend to use.
	 */
	protected AbstractStateBackend stateBackend;

	/**
	 * Shared between all the test. Make sure to have at least NUM_SLOTS
	 * available after your test finishes, e.g. cancel the job you submitted.
	 */
	protected static FlinkMiniCluster cluster;

	/**
	 * Client shared between all the test.
	 */
	protected static QueryableStateClient client;

	protected static int maxParallelism;

	@Before
	public void setUp() throws Exception {
		// NOTE: do not use a shared instance for all tests as the tests may brake
		this.stateBackend = createStateBackend();

		Assert.assertNotNull(cluster);

		maxParallelism = cluster.configuration().getInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 1) *
				cluster.configuration().getInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, 1);
	}

	/**
	 * Creates a state backend instance which is used in the {@link #setUp()} method before each
	 * test case.
	 *
	 * @return a state backend instance for each unit test
	 */
	protected abstract AbstractStateBackend createStateBackend() throws Exception;

	/**
	 * Runs a simple topology producing random (key, 1) pairs at the sources (where
	 * number of keys is in fixed in range 0...numKeys). The records are keyed and
	 * a reducing queryable state instance is created, which sums up the records.
	 *
	 * <p>After submitting the job in detached mode, the QueryableStateCLient is used
	 * to query the counts of each key in rounds until all keys have non-zero counts.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testQueryableState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();
		final int numKeys = 256;

		JobID jobId = null;

		try {
			//
			// Test program
			//
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestKeyRangeSource(numKeys));

			// Reducing state
			ReducingStateDescriptor<Tuple2<Integer, Long>> reducingState = new ReducingStateDescriptor<>(
					"any-name",
					new SumReduce(),
					source.getType());

			final String queryName = "hakuna-matata";

			source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
				private static final long serialVersionUID = 7143749578983540352L;

				@Override
				public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
					return value.f0;
				}
			}).asQueryableState(queryName, reducingState);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			cluster.submitJobDetached(jobGraph);

			//
			// Start querying
			//
			jobId = jobGraph.getJobID();

			final AtomicLongArray counts = new AtomicLongArray(numKeys);

			boolean allNonZero = false;
			while (!allNonZero && deadline.hasTimeLeft()) {
				allNonZero = true;

				final List<CompletableFuture<ReducingState<Tuple2<Integer, Long>>>> futures = new ArrayList<>(numKeys);

				for (int i = 0; i < numKeys; i++) {
					final int key = i;

					if (counts.get(key) > 0L) {
						// Skip this one
						continue;
					} else {
						allNonZero = false;
					}

					CompletableFuture<ReducingState<Tuple2<Integer, Long>>> result = getKvStateWithRetries(
							client,
							jobId,
							queryName,
							key,
							BasicTypeInfo.INT_TYPE_INFO,
							reducingState,
							QUERY_RETRY_DELAY,
							false,
							executor);

					result.thenAccept(response -> {
						try {
							Tuple2<Integer, Long> res = response.get();
							counts.set(key, res.f1);
							assertEquals("Key mismatch", key, res.f0.intValue());
						} catch (Exception e) {
							Assert.fail(e.getMessage());
						}
					});

					futures.add(result);
				}

				// wait for all the futures to complete
				CompletableFuture
						.allOf(futures.toArray(new CompletableFuture<?>[futures.size()]))
						.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}

			assertTrue("Not all keys are non-zero", allNonZero);

			// All should be non-zero
			for (int i = 0; i < numKeys; i++) {
				long count = counts.get(i);
				assertTrue("Count at position " + i + " is " + count, count > 0);
			}
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests that duplicate query registrations fail the job at the JobManager.
	 *
	 * <b>NOTE: </b> This test is only in the non-HA variant of the tests because
	 * in the HA mode we use the actual JM code which does not recognize the
	 * {@code NotifyWhenJobStatus} message.	 *
	 */
	@Test
	public void testDuplicateRegistrationFailsJob() throws Exception {
		final Deadline deadline = TEST_TIMEOUT.fromNow();
		final int numKeys = 256;

		JobID jobId = null;

		try {
			//
			// Test program
			//
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestKeyRangeSource(numKeys));

			// Reducing state
			ReducingStateDescriptor<Tuple2<Integer, Long>> reducingState = new ReducingStateDescriptor<>(
					"any-name",
					new SumReduce(),
					source.getType());

			final String queryName = "duplicate-me";

			final QueryableStateStream<Integer, Tuple2<Integer, Long>> queryableState =
					source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
						private static final long serialVersionUID = -4126824763829132959L;

						@Override
						public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
							return value.f0;
						}
					}).asQueryableState(queryName, reducingState);

			final QueryableStateStream<Integer, Tuple2<Integer, Long>> duplicate =
					source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
						private static final long serialVersionUID = -6265024000462809436L;

						@Override
						public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
							return value.f0;
						}
					}).asQueryableState(queryName);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			CompletableFuture<TestingJobManagerMessages.JobStatusIs> failedFuture = FutureUtils.toJava(
					cluster.getLeaderGateway(deadline.timeLeft())
							.ask(new TestingJobManagerMessages.NotifyWhenJobStatus(jobId, JobStatus.FAILED), deadline.timeLeft())
							.mapTo(ClassTag$.MODULE$.<TestingJobManagerMessages.JobStatusIs>apply(TestingJobManagerMessages.JobStatusIs.class)));

			cluster.submitJobDetached(jobGraph);

			TestingJobManagerMessages.JobStatusIs jobStatus =
					failedFuture.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			assertEquals(JobStatus.FAILED, jobStatus.state());

			// Get the job and check the cause
			JobManagerMessages.JobFound jobFound = FutureUtils.toJava(
					cluster.getLeaderGateway(deadline.timeLeft())
							.ask(new JobManagerMessages.RequestJob(jobId), deadline.timeLeft())
							.mapTo(ClassTag$.MODULE$.<JobManagerMessages.JobFound>apply(JobManagerMessages.JobFound.class)))
					.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);

			String failureCause = jobFound.executionGraph().getFailureCause().getExceptionAsString();

			assertTrue("Not instance of SuppressRestartsException", failureCause.startsWith("org.apache.flink.runtime.execution.SuppressRestartsException"));
			int causedByIndex = failureCause.indexOf("Caused by: ");
			String subFailureCause = failureCause.substring(causedByIndex + "Caused by: ".length());
			assertTrue("Not caused by IllegalStateException", subFailureCause.startsWith("java.lang.IllegalStateException"));
			assertTrue("Exception does not contain registration name", subFailureCause.contains(queryName));
		} finally {
			// Free cluster resources
			if (jobId != null) {
				scala.concurrent.Future<CancellationSuccess> cancellation = cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<JobManagerMessages.CancellationSuccess>apply(JobManagerMessages.CancellationSuccess.class));

				Await.ready(cancellation, deadline.timeLeft());
			}
		}
	}

	/**
	 * Tests simple value state queryable state instance. Each source emits
	 * (subtaskIndex, 0)..(subtaskIndex, numElements) tuples, which are then
	 * queried. The tests succeeds after each subtask index is queried with
	 * value numElements (the latest element updated the state).
	 */
	@Test
	public void testValueState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			// Value state
			ValueStateDescriptor<Tuple2<Integer, Long>> valueState = new ValueStateDescriptor<>(
					"any",
					source.getType());

			source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
				private static final long serialVersionUID = 7662520075515707428L;

				@Override
				public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
					return value.f0;
				}
			}).asQueryableState("hakuna", valueState);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			executeValueQuery(deadline, client, jobId, "hakuna", valueState, numElements);
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Similar tests as {@link #testValueState()} but before submitting the
	 * job, we already issue one request which fails.
	 */
	@Test
	public void testQueryNonStartedJobState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
				.addSource(new TestAscendingValueSource(numElements));

			// Value state
			ValueStateDescriptor<Tuple2<Integer, Long>> valueState = new ValueStateDescriptor<>(
				"any",
				source.getType(),
				null);

			QueryableStateStream<Integer, Tuple2<Integer, Long>> queryableState =
				source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
					private static final long serialVersionUID = 7480503339992214681L;

					@Override
					public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
						return value.f0;
					}
				}).asQueryableState("hakuna", valueState);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			// Now query
			long expected = numElements;

			// query once
			client.getKvState(
					jobId,
					queryableState.getQueryableStateName(),
					0,
					VoidNamespace.INSTANCE,
					BasicTypeInfo.INT_TYPE_INFO,
					VoidNamespaceTypeInfo.INSTANCE,
					valueState);

			cluster.submitJobDetached(jobGraph);

			executeValueQuery(deadline, client, jobId, "hakuna", valueState, expected);
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests simple value state queryable state instance with a default value
	 * set. Each source emits (subtaskIndex, 0)..(subtaskIndex, numElements)
	 * tuples, the key is mapped to 1 but key 0 is queried which should throw
	 * a {@link UnknownKeyOrNamespaceException} exception.
	 *
	 * @throws UnknownKeyOrNamespaceException thrown due querying a non-existent key
	 */
	@Test(expected = UnknownKeyOrNamespaceException.class)
	public void testValueStateDefault() throws Throwable {

		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env =
				StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies
				.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
				.addSource(new TestAscendingValueSource(numElements));

			// Value state
			ValueStateDescriptor<Tuple2<Integer, Long>> valueState =
				new ValueStateDescriptor<>(
					"any",
					source.getType(),
					Tuple2.of(0, 1337L));

			// only expose key "1"
			QueryableStateStream<Integer, Tuple2<Integer, Long>>
				queryableState =
				source.keyBy(
					new KeySelector<Tuple2<Integer, Long>, Integer>() {
						private static final long serialVersionUID = 4509274556892655887L;

						@Override
						public Integer getKey(
							Tuple2<Integer, Long> value) throws
							Exception {
							return 1;
						}
					}).asQueryableState("hakuna", valueState);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			// Now query
			int key = 0;
			CompletableFuture<ValueState<Tuple2<Integer, Long>>> future = getKvStateWithRetries(
					client,
					jobId,
					queryableState.getQueryableStateName(),
					key,
					BasicTypeInfo.INT_TYPE_INFO,
					valueState,
					QUERY_RETRY_DELAY,
					true,
					executor);

			try {
				future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			} catch (ExecutionException | CompletionException e) {
				// get() on a completedExceptionally future wraps the
				// exception in an ExecutionException.
				throw e.getCause();
			}
		} finally {

			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests simple value state queryable state instance. Each source emits
	 * (subtaskIndex, 0)..(subtaskIndex, numElements) tuples, which are then
	 * queried. The tests succeeds after each subtask index is queried with
	 * value numElements (the latest element updated the state).
	 *
	 * <p>This is the same as the simple value state test, but uses the API shortcut.
	 */
	@Test
	public void testValueStateShortcut() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			// Value state shortcut
			QueryableStateStream<Integer, Tuple2<Integer, Long>> queryableState =
					source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
						private static final long serialVersionUID = 9168901838808830068L;

						@Override
						public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
							return value.f0;
						}
					}).asQueryableState("matata");

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			final ValueStateDescriptor<Tuple2<Integer, Long>> stateDesc =
					(ValueStateDescriptor<Tuple2<Integer, Long>>) queryableState.getStateDescriptor();
			executeValueQuery(deadline, client, jobId, "matata", stateDesc, numElements);
		} finally {

			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(
						cluster.getLeaderGateway(deadline.timeLeft())
								.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
								.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests simple folding state queryable state instance. Each source emits
	 * (subtaskIndex, 0)..(subtaskIndex, numElements) tuples, which are then
	 * queried. The folding state sums these up and maps them to Strings. The
	 * test succeeds after each subtask index is queried with result n*(n+1)/2
	 * (as a String).
	 */
	@Test
	public void testFoldingState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final int numElements = 1024;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			// Folding state
			FoldingStateDescriptor<Tuple2<Integer, Long>, String> foldingState =
					new FoldingStateDescriptor<>(
							"any",
							"0",
							new SumFold(),
							StringSerializer.INSTANCE);

			QueryableStateStream<Integer, String> queryableState =
					source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
						private static final long serialVersionUID = -842809958106747539L;

						@Override
						public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
							return value.f0;
						}
					}).asQueryableState("pumba", foldingState);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			// Now query
			String expected = Integer.toString(numElements * (numElements + 1) / 2);

			for (int key = 0; key < maxParallelism; key++) {
				boolean success = false;
				while (deadline.hasTimeLeft() && !success) {
					CompletableFuture<FoldingState<Tuple2<Integer, Long>, String>> future = getKvStateWithRetries(
							client,
							jobId,
							"pumba",
							key,
							BasicTypeInfo.INT_TYPE_INFO,
							foldingState,
							QUERY_RETRY_DELAY,
							false,
							executor);

					String value = future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS).get();

					//assertEquals("Key mismatch", key, value.f0.intValue());
					if (expected.equals(value)) {
						success = true;
					} else {
						// Retry
						Thread.sleep(50L);
					}
				}

				assertTrue("Did not succeed query", success);
			}
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests simple reducing state queryable state instance. Each source emits
	 * (subtaskIndex, 0)..(subtaskIndex, numElements) tuples, which are then
	 * queried. The reducing state instance sums these up. The test succeeds
	 * after each subtask index is queried with result n*(n+1)/2.
	 */
	@Test
	public void testReducingState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			// Reducing state
			ReducingStateDescriptor<Tuple2<Integer, Long>> reducingState =
					new ReducingStateDescriptor<>(
							"any",
							new SumReduce(),
							source.getType());

			source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
				private static final long serialVersionUID = 8470749712274833552L;

				@Override
				public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
					return value.f0;
				}
			}).asQueryableState("jungle", reducingState);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			// Now query
			long expected = numElements * (numElements + 1L) / 2L;

			for (int key = 0; key < maxParallelism; key++) {
				boolean success = false;
				while (deadline.hasTimeLeft() && !success) {
					CompletableFuture<ReducingState<Tuple2<Integer, Long>>> future = getKvStateWithRetries(
							client,
							jobId,
							"jungle",
							key,
							BasicTypeInfo.INT_TYPE_INFO,
							reducingState,
							QUERY_RETRY_DELAY,
							false,
							executor);

					Tuple2<Integer, Long> value = future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS).get();

					assertEquals("Key mismatch", key, value.f0.intValue());
					if (expected == value.f1) {
						success = true;
					} else {
						// Retry
						Thread.sleep(50L);
					}
				}

				assertTrue("Did not succeed query", success);
			}
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests simple map state queryable state instance. Each source emits
	 * (subtaskIndex, 0)..(subtaskIndex, numElements) tuples, which are then
	 * queried. The map state instance sums the values up. The test succeeds
	 * after each subtask index is queried with result n*(n+1)/2.
	 */
	@Test
	public void testMapState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			final MapStateDescriptor<Integer, Tuple2<Integer, Long>> mapStateDescriptor = new MapStateDescriptor<>(
					"timon",
					BasicTypeInfo.INT_TYPE_INFO,
					source.getType());
			mapStateDescriptor.setQueryable("timon-queryable");

			source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
				private static final long serialVersionUID = 8470749712274833552L;

				@Override
				public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
					return value.f0;
				}
			}).process(new ProcessFunction<Tuple2<Integer, Long>, Object>() {
				private static final long serialVersionUID = -805125545438296619L;

				private transient MapState<Integer, Tuple2<Integer, Long>> mapState;

				@Override
				public void open(Configuration parameters) throws Exception {
					super.open(parameters);
					mapState = getRuntimeContext().getMapState(mapStateDescriptor);
				}

				@Override
				public void processElement(Tuple2<Integer, Long> value, Context ctx, Collector<Object> out) throws Exception {
					Tuple2<Integer, Long> v = mapState.get(value.f0);
					if (v == null) {
						v = new Tuple2<>(value.f0, 0L);
					}
					mapState.put(value.f0, new Tuple2<>(v.f0, v.f1 + value.f1));
				}
			});

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			// Now query
			long expected = numElements * (numElements + 1L) / 2L;

			for (int key = 0; key < maxParallelism; key++) {
				boolean success = false;
				while (deadline.hasTimeLeft() && !success) {
					CompletableFuture<MapState<Integer, Tuple2<Integer, Long>>> future = getKvStateWithRetries(
							client,
							jobId,
							"timon-queryable",
							key,
							BasicTypeInfo.INT_TYPE_INFO,
							mapStateDescriptor,
							QUERY_RETRY_DELAY,
							false,
							executor);

					Tuple2<Integer, Long> value = future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS).get(key);
					assertEquals("Key mismatch", key, value.f0.intValue());
					if (expected == value.f1) {
						success = true;
					} else {
						// Retry
						Thread.sleep(50L);
					}
				}

				assertTrue("Did not succeed query", success);
			}
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Tests simple list state queryable state instance. Each source emits
	 * (subtaskIndex, 0)..(subtaskIndex, numElements) tuples, which are then
	 * queried. The list state instance add the values to the list. The test
	 * succeeds after each subtask index is queried and the list contains
	 * the correct number of distinct elements.
	 */
	@Test
	public void testListState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			final ListStateDescriptor<Long> listStateDescriptor = new ListStateDescriptor<Long>(
					"list",
					BasicTypeInfo.LONG_TYPE_INFO);
			listStateDescriptor.setQueryable("list-queryable");

			source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
				private static final long serialVersionUID = 8470749712274833552L;

				@Override
				public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
					return value.f0;
				}
			}).process(new ProcessFunction<Tuple2<Integer, Long>, Object>() {
				private static final long serialVersionUID = -805125545438296619L;

				private transient ListState<Long> listState;

				@Override
				public void open(Configuration parameters) throws Exception {
					super.open(parameters);
					listState = getRuntimeContext().getListState(listStateDescriptor);
				}

				@Override
				public void processElement(Tuple2<Integer, Long> value, Context ctx, Collector<Object> out) throws Exception {
					listState.add(value.f1);
				}
			});

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			// Now query

			Map<Integer, Set<Long>> results = new HashMap<>();

			for (int key = 0; key < maxParallelism; key++) {
				boolean success = false;
				while (deadline.hasTimeLeft() && !success) {
					CompletableFuture<ListState<Long>> future = getKvStateWithRetries(
							client,
							jobId,
							"list-queryable",
							key,
							BasicTypeInfo.INT_TYPE_INFO,
							listStateDescriptor,
							QUERY_RETRY_DELAY,
							false,
							executor);

					Iterable<Long> value = future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS).get();

					Set<Long> res = new HashSet<>();
					for (Long v: value) {
						res.add(v);
					}

					// the source starts at 0, so +1
					if (res.size() == numElements + 1L) {
						success = true;
						results.put(key, res);
					} else {
						// Retry
						Thread.sleep(50L);
					}
				}

				assertTrue("Did not succeed query", success);
			}

			for (int key = 0; key < maxParallelism; key++) {
				Set<Long> values = results.get(key);
				for (long i = 0L; i <= numElements; i++) {
					assertTrue(values.contains(i));
				}
			}

		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	@Test
	public void testAggregatingState() throws Exception {
		// Config
		final Deadline deadline = TEST_TIMEOUT.fromNow();

		final long numElements = 1024L;

		JobID jobId = null;
		try {
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
			env.setStateBackend(stateBackend);
			env.setParallelism(maxParallelism);
			// Very important, because cluster is shared between tests and we
			// don't explicitly check that all slots are available before
			// submitting.
			env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, 1000L));

			DataStream<Tuple2<Integer, Long>> source = env
					.addSource(new TestAscendingValueSource(numElements));

			final AggregatingStateDescriptor<Tuple2<Integer, Long>, String, String> aggrStateDescriptor =
					new AggregatingStateDescriptor<>(
							"aggregates",
							new SumAggr(),
							String.class);
			aggrStateDescriptor.setQueryable("aggr-queryable");

			source.keyBy(new KeySelector<Tuple2<Integer, Long>, Integer>() {
				private static final long serialVersionUID = 8470749712274833552L;

				@Override
				public Integer getKey(Tuple2<Integer, Long> value) throws Exception {
					return value.f0;
				}
			}).transform(
					"TestAggregatingOperator",
					BasicTypeInfo.STRING_TYPE_INFO,
					new AggregatingTestOperator(aggrStateDescriptor)
			);

			// Submit the job graph
			JobGraph jobGraph = env.getStreamGraph().getJobGraph();
			jobId = jobGraph.getJobID();

			cluster.submitJobDetached(jobGraph);

			// Now query

			for (int key = 0; key < maxParallelism; key++) {
				boolean success = false;
				while (deadline.hasTimeLeft() && !success) {
					CompletableFuture<AggregatingState<Tuple2<Integer, Long>, String>> future = getKvStateWithRetries(
							client,
							jobId,
							"aggr-queryable",
							key,
							BasicTypeInfo.INT_TYPE_INFO,
							aggrStateDescriptor,
							QUERY_RETRY_DELAY,
							false,
							executor);

					String value = future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS).get();

					if (Long.parseLong(value) == numElements * (numElements + 1L) / 2L) {
						success = true;
					} else {
						// Retry
						Thread.sleep(50L);
					}
				}

				assertTrue("Did not succeed query", success);
			}
		} finally {
			// Free cluster resources
			if (jobId != null) {
				CompletableFuture<CancellationSuccess> cancellation = FutureUtils.toJava(cluster
						.getLeaderGateway(deadline.timeLeft())
						.ask(new JobManagerMessages.CancelJob(jobId), deadline.timeLeft())
						.mapTo(ClassTag$.MODULE$.<CancellationSuccess>apply(CancellationSuccess.class)));

				cancellation.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/////				Sources/UDFs Used in the Tests			//////

	/**
	 * Test source producing (key, 0)..(key, maxValue) with key being the sub
	 * task index.
	 *
	 * <p>After all tuples have been emitted, the source waits to be cancelled
	 * and does not immediately finish.
	 */
	private static class TestAscendingValueSource extends RichParallelSourceFunction<Tuple2<Integer, Long>> {

		private static final long serialVersionUID = 1459935229498173245L;

		private final long maxValue;
		private volatile boolean isRunning = true;

		TestAscendingValueSource(long maxValue) {
			Preconditions.checkArgument(maxValue >= 0);
			this.maxValue = maxValue;
		}

		@Override
		public void open(Configuration parameters) throws Exception {
			super.open(parameters);
		}

		@Override
		public void run(SourceContext<Tuple2<Integer, Long>> ctx) throws Exception {
			// f0 => key
			int key = getRuntimeContext().getIndexOfThisSubtask();
			Tuple2<Integer, Long> record = new Tuple2<>(key, 0L);

			long currentValue = 0;
			while (isRunning && currentValue <= maxValue) {
				synchronized (ctx.getCheckpointLock()) {
					record.f1 = currentValue;
					ctx.collect(record);
				}

				currentValue++;
			}

			while (isRunning) {
				synchronized (this) {
					wait();
				}
			}
		}

		@Override
		public void cancel() {
			isRunning = false;

			synchronized (this) {
				notifyAll();
			}
		}

	}

	/**
	 * Test source producing (key, 1) tuples with random key in key range (numKeys).
	 */
	private static class TestKeyRangeSource extends RichParallelSourceFunction<Tuple2<Integer, Long>> implements CheckpointListener {

		private static final long serialVersionUID = -5744725196953582710L;

		private static final AtomicLong LATEST_CHECKPOINT_ID = new AtomicLong();
		private final int numKeys;
		private final ThreadLocalRandom random = ThreadLocalRandom.current();
		private volatile boolean isRunning = true;

		TestKeyRangeSource(int numKeys) {
			this.numKeys = numKeys;
		}

		@Override
		public void open(Configuration parameters) throws Exception {
			super.open(parameters);
			if (getRuntimeContext().getIndexOfThisSubtask() == 0) {
				LATEST_CHECKPOINT_ID.set(0L);
			}
		}

		@Override
		public void run(SourceContext<Tuple2<Integer, Long>> ctx) throws Exception {
			// f0 => key
			Tuple2<Integer, Long> record = new Tuple2<>(0, 1L);

			while (isRunning) {
				synchronized (ctx.getCheckpointLock()) {
					record.f0 = random.nextInt(numKeys);
					ctx.collect(record);
				}
				// mild slow down
				Thread.sleep(1L);
			}
		}

		@Override
		public void cancel() {
			isRunning = false;
		}

		@Override
		public void notifyCheckpointComplete(long checkpointId) throws Exception {
			if (getRuntimeContext().getIndexOfThisSubtask() == 0) {
				LATEST_CHECKPOINT_ID.set(checkpointId);
			}
		}
	}

	/**
	 * An operator that uses {@link AggregatingState}.
	 *
	 * <p>The operator exists for lack of possibility to get an
	 * {@link AggregatingState} from the {@link org.apache.flink.api.common.functions.RuntimeContext}.
	 * If this were not the case, we could have a {@link ProcessFunction}.
	 */
	private static class AggregatingTestOperator
			extends AbstractStreamOperator<String>
			implements OneInputStreamOperator<Tuple2<Integer, Long>, String> {

		private static final long serialVersionUID = 1L;

		private final AggregatingStateDescriptor<Tuple2<Integer, Long>, String, String> stateDescriptor;
		private transient AggregatingState<Tuple2<Integer, Long>, String> state;

		AggregatingTestOperator(AggregatingStateDescriptor<Tuple2<Integer, Long>, String, String> stateDesc) {
			this.stateDescriptor = stateDesc;
		}

		@Override
		public void open() throws Exception {
			super.open();
			this.state = getKeyedStateBackend().getPartitionedState(
					VoidNamespace.INSTANCE,
					VoidNamespaceSerializer.INSTANCE,
					stateDescriptor);
		}

		@Override
		public void processElement(StreamRecord<Tuple2<Integer, Long>> element) throws Exception {
			state.add(element.getValue());
		}
	}

	/**
	 * Test {@link AggregateFunction} concatenating the already stored string with the long passed as argument.
	 */
	private static class SumAggr implements AggregateFunction<Tuple2<Integer, Long>, String, String> {

		private static final long serialVersionUID = -6249227626701264599L;

		@Override
		public String createAccumulator() {
			return "0";
		}

		@Override
		public String add(Tuple2<Integer, Long> value, String accumulator) {
			long acc = Long.valueOf(accumulator);
			acc += value.f1;
			return Long.toString(acc);
		}

		@Override
		public String getResult(String accumulator) {
			return accumulator;
		}

		@Override
		public String merge(String a, String b) {
			return Long.toString(Long.valueOf(a) + Long.valueOf(b));
		}
	}

	/**
	 * Test {@link FoldFunction} concatenating the already stored string with the long passed as argument.
	 */
	private static class SumFold implements FoldFunction<Tuple2<Integer, Long>, String> {
		private static final long serialVersionUID = -6249227626701264599L;

		@Override
		public String fold(String accumulator, Tuple2<Integer, Long> value) throws Exception {
			long acc = Long.valueOf(accumulator);
			acc += value.f1;
			return Long.toString(acc);
		}
	}

	/**
	 * Test {@link ReduceFunction} summing up its two arguments.
	 */
	protected static class SumReduce implements ReduceFunction<Tuple2<Integer, Long>> {
		private static final long serialVersionUID = -8651235077342052336L;

		@Override
		public Tuple2<Integer, Long> reduce(Tuple2<Integer, Long> value1, Tuple2<Integer, Long> value2) throws Exception {
			value1.f1 += value2.f1;
			return value1;
		}
	}

	/////				General Utility Methods				//////

	private static <K, S extends State, V> CompletableFuture<S> getKvStateWithRetries(
			final QueryableStateClient client,
			final JobID jobId,
			final String queryName,
			final K key,
			final TypeInformation<K> keyTypeInfo,
			final StateDescriptor<S, V> stateDescriptor,
			final Time retryDelay,
			final boolean failForUnknownKeyOrNamespace,
			final ScheduledExecutor executor) {
		return retryWithDelay(
				() -> client.getKvState(jobId, queryName, key, VoidNamespace.INSTANCE, keyTypeInfo, VoidNamespaceTypeInfo.INSTANCE, stateDescriptor),
				NO_OF_RETRIES,
				retryDelay,
				executor,
				failForUnknownKeyOrNamespace);
	}

	private static <T> CompletableFuture<T> retryWithDelay(
			final Supplier<CompletableFuture<T>> operation,
			final int retries,
			final Time retryDelay,
			final ScheduledExecutor scheduledExecutor,
			final boolean failIfUnknownKeyOrNamespace) {

		final CompletableFuture<T> resultFuture = new CompletableFuture<>();

		retryWithDelay(
				resultFuture,
				operation,
				retries,
				retryDelay,
				scheduledExecutor,
				failIfUnknownKeyOrNamespace);

		return resultFuture;
	}

	public static <T> void retryWithDelay(
			final CompletableFuture<T> resultFuture,
			final Supplier<CompletableFuture<T>> operation,
			final int retries,
			final Time retryDelay,
			final ScheduledExecutor scheduledExecutor,
			final boolean failIfUnknownKeyOrNamespace) {

		if (!resultFuture.isDone()) {
			final CompletableFuture<T> operationResultFuture = operation.get();
			operationResultFuture.whenCompleteAsync(
					(t, throwable) -> {
						if (throwable != null) {
							if (throwable.getCause() instanceof CancellationException) {
								resultFuture.completeExceptionally(new FutureUtils.RetryException("Operation future was cancelled.", throwable.getCause()));
							} else if (throwable.getCause() instanceof AssertionError ||
									(failIfUnknownKeyOrNamespace && throwable.getCause() instanceof UnknownKeyOrNamespaceException)) {
								resultFuture.completeExceptionally(throwable.getCause());
							} else {
								if (retries > 0) {
									final ScheduledFuture<?> scheduledFuture = scheduledExecutor.schedule(
											() -> retryWithDelay(resultFuture, operation, retries - 1, retryDelay, scheduledExecutor, failIfUnknownKeyOrNamespace),
											retryDelay.toMilliseconds(),
											TimeUnit.MILLISECONDS);

									resultFuture.whenComplete(
											(innerT, innerThrowable) -> scheduledFuture.cancel(false));
								} else {
									resultFuture.completeExceptionally(new FutureUtils.RetryException("Could not complete the operation. Number of retries " +
											"has been exhausted.", throwable));
								}
							}
						} else {
							resultFuture.complete(t);
						}
					},
					scheduledExecutor);

			resultFuture.whenComplete(
					(t, throwable) -> operationResultFuture.cancel(false));
		}
	}

	/**
	 * Retry a query for state for keys between 0 and {@link #maxParallelism} until
	 * <tt>expected</tt> equals the value of the result tuple's second field.
	 */
	private void executeValueQuery(
			final Deadline deadline,
			final QueryableStateClient client,
			final JobID jobId,
			final String queryableStateName,
			final ValueStateDescriptor<Tuple2<Integer, Long>> stateDescriptor,
			final long expected) throws Exception {

		for (int key = 0; key < maxParallelism; key++) {
			boolean success = false;
			while (deadline.hasTimeLeft() && !success) {
				CompletableFuture<ValueState<Tuple2<Integer, Long>>> future = getKvStateWithRetries(
						client,
						jobId,
						queryableStateName,
						key,
						BasicTypeInfo.INT_TYPE_INFO,
						stateDescriptor,
						QUERY_RETRY_DELAY,
						false,
						executor);

				Tuple2<Integer, Long> value = future.get(deadline.timeLeft().toMillis(), TimeUnit.MILLISECONDS).value();

				assertEquals("Key mismatch", key, value.f0.intValue());
				if (expected == value.f1) {
					success = true;
				} else {
					// Retry
					Thread.sleep(50L);
				}
			}

			assertTrue("Did not succeed query", success);
		}
	}
}
