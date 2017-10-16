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

package org.apache.flink.runtime.entrypoint;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.blob.BlobServer;
<<<<<<< HEAD
import org.apache.flink.runtime.blob.BlobService;
=======
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.heartbeat.HeartbeatServices;
import org.apache.flink.runtime.highavailability.HighAvailabilityServices;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobmanager.OnCompletionActions;
import org.apache.flink.runtime.jobmaster.JobManagerRunner;
<<<<<<< HEAD
=======
import org.apache.flink.runtime.jobmaster.JobManagerServices;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import org.apache.flink.runtime.metrics.MetricRegistry;
import org.apache.flink.runtime.resourcemanager.ResourceManager;
import org.apache.flink.runtime.rpc.FatalErrorHandler;
import org.apache.flink.runtime.rpc.RpcService;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FlinkException;
import org.apache.flink.util.Preconditions;

/**
 * Base class for per-job cluster entry points.
 */
public abstract class JobClusterEntrypoint extends ClusterEntrypoint {

	private ResourceManager<?> resourceManager;

<<<<<<< HEAD
=======
	private JobManagerServices jobManagerServices;

>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
	private JobManagerRunner jobManagerRunner;

	public JobClusterEntrypoint(Configuration configuration) {
		super(configuration);
	}

	@Override
	protected void startClusterComponents(
			Configuration configuration,
			RpcService rpcService,
			HighAvailabilityServices highAvailabilityServices,
			BlobServer blobServer,
			HeartbeatServices heartbeatServices,
			MetricRegistry metricRegistry) throws Exception {

		resourceManager = createResourceManager(
			configuration,
			ResourceID.generate(),
			rpcService,
			highAvailabilityServices,
			heartbeatServices,
			metricRegistry,
			this);

<<<<<<< HEAD
=======
		jobManagerServices = JobManagerServices.fromConfiguration(configuration, blobServer);

>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		jobManagerRunner = createJobManagerRunner(
			configuration,
			ResourceID.generate(),
			rpcService,
			highAvailabilityServices,
<<<<<<< HEAD
			blobServer,
=======
			jobManagerServices,
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
			heartbeatServices,
			metricRegistry,
			this);

		LOG.debug("Starting ResourceManager.");
		resourceManager.start();

		LOG.debug("Starting JobManager.");
		jobManagerRunner.start();
	}

	protected JobManagerRunner createJobManagerRunner(
			Configuration configuration,
			ResourceID resourceId,
			RpcService rpcService,
			HighAvailabilityServices highAvailabilityServices,
<<<<<<< HEAD
			BlobService blobService,
=======
			JobManagerServices jobManagerServices,
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
			HeartbeatServices heartbeatServices,
			MetricRegistry metricRegistry,
			FatalErrorHandler fatalErrorHandler) throws Exception {

		JobGraph jobGraph = retrieveJobGraph(configuration);

		return new JobManagerRunner(
			resourceId,
			jobGraph,
			configuration,
			rpcService,
			highAvailabilityServices,
<<<<<<< HEAD
			blobService,
			heartbeatServices,
=======
			heartbeatServices,
			jobManagerServices,
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
			metricRegistry,
			new TerminatingOnCompleteActions(jobGraph.getJobID()),
			fatalErrorHandler);
	}

	@Override
<<<<<<< HEAD
	protected void shutDown(boolean cleanupHaData) throws FlinkException {
=======
	protected void stopClusterComponents(boolean cleanupHaData) throws Exception {
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		Throwable exception = null;

		if (jobManagerRunner != null) {
			try {
				jobManagerRunner.shutdown();
			} catch (Throwable t) {
				exception = t;
			}
		}

<<<<<<< HEAD
		if (resourceManager != null) {
			try {
				resourceManager.shutDown();
=======
		if (jobManagerServices != null) {
			try {
				jobManagerServices.shutdown();
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
			} catch (Throwable t) {
				exception = ExceptionUtils.firstOrSuppressed(t, exception);
			}
		}

<<<<<<< HEAD
		try {
			super.shutDown(cleanupHaData);
		} catch (Throwable t) {
			exception = ExceptionUtils.firstOrSuppressed(t, exception);
		}

		if (exception != null) {
			throw new FlinkException("Could not properly shut down the session cluster entry point.", exception);
=======
		if (resourceManager != null) {
			try {
				resourceManager.shutDown();
			} catch (Throwable t) {
				exception = ExceptionUtils.firstOrSuppressed(t, exception);
			}
		}

		if (exception != null) {
			throw new FlinkException("Could not properly shut down the job cluster entry point.", exception);
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		}
	}

	private void shutDownAndTerminate(boolean cleanupHaData) {
		try {
			shutDown(cleanupHaData);
		} catch (Throwable t) {
			LOG.error("Could not properly shut down cluster entrypoint.", t);
		}

		System.exit(SUCCESS_RETURN_CODE);
	}

	protected abstract ResourceManager<?> createResourceManager(
		Configuration configuration,
		ResourceID resourceId,
		RpcService rpcService,
		HighAvailabilityServices highAvailabilityServices,
		HeartbeatServices heartbeatServices,
		MetricRegistry metricRegistry,
		FatalErrorHandler fatalErrorHandler) throws Exception;

	protected abstract JobGraph retrieveJobGraph(Configuration configuration) throws FlinkException;

	private final class TerminatingOnCompleteActions implements OnCompletionActions {

		private final JobID jobId;

		private TerminatingOnCompleteActions(JobID jobId) {
			this.jobId = Preconditions.checkNotNull(jobId);
		}

		@Override
		public void jobFinished(JobExecutionResult result) {
			LOG.info("Job({}) finished.", jobId);

			shutDownAndTerminate(true);
		}

		@Override
		public void jobFailed(Throwable cause) {
			LOG.info("Job({}) failed.", jobId, cause);

			shutDownAndTerminate(false);
		}

		@Override
		public void jobFinishedByOther() {
			LOG.info("Job({}) was finished by another JobManager.", jobId);

			shutDownAndTerminate(false);
		}
	}
}
