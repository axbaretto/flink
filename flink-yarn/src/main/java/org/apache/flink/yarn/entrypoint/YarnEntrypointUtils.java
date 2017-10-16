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

package org.apache.flink.yarn.entrypoint;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.configuration.HighAvailabilityOptions;
import org.apache.flink.configuration.JobManagerOptions;
<<<<<<< HEAD
import org.apache.flink.configuration.SecurityOptions;
import org.apache.flink.runtime.clusterframework.BootstrapTools;
import org.apache.flink.runtime.security.SecurityContext;
import org.apache.flink.runtime.security.SecurityUtils;
=======
import org.apache.flink.configuration.ResourceManagerOptions;
import org.apache.flink.configuration.SecurityOptions;
import org.apache.flink.configuration.WebOptions;
import org.apache.flink.runtime.clusterframework.BootstrapTools;
import org.apache.flink.runtime.security.SecurityConfiguration;
import org.apache.flink.runtime.security.SecurityContext;
import org.apache.flink.runtime.security.SecurityUtils;
import org.apache.flink.runtime.security.modules.HadoopModule;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import org.apache.flink.util.Preconditions;
import org.apache.flink.yarn.Utils;
import org.apache.flink.yarn.YarnConfigKeys;
import org.apache.flink.yarn.cli.FlinkYarnSessionCli;

import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
<<<<<<< HEAD
=======
import java.util.Collections;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import java.util.Map;

/**
 * This class contains utility methods for the {@link YarnSessionClusterEntrypoint} and
 * {@link YarnJobClusterEntrypoint}.
 */
public class YarnEntrypointUtils {

	public static SecurityContext installSecurityContext(
			Configuration configuration,
			String workingDirectory) throws Exception {
<<<<<<< HEAD
		org.apache.hadoop.conf.Configuration hadoopConfiguration = null;
=======

		SecurityConfiguration sc;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d

		//To support Yarn Secure Integration Test Scenario
		File krb5Conf = new File(workingDirectory, Utils.KRB5_FILE_NAME);
		if (krb5Conf.exists() && krb5Conf.canRead()) {
<<<<<<< HEAD
			hadoopConfiguration = new org.apache.hadoop.conf.Configuration();
			hadoopConfiguration.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION, "kerberos");
			hadoopConfiguration.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHORIZATION, "true");
		}

		SecurityUtils.SecurityConfiguration sc;
		if (hadoopConfiguration != null) {
			sc = new SecurityUtils.SecurityConfiguration(configuration, hadoopConfiguration);
		} else {
			sc = new SecurityUtils.SecurityConfiguration(configuration);
=======
			org.apache.hadoop.conf.Configuration hadoopConfiguration = new org.apache.hadoop.conf.Configuration();
			hadoopConfiguration.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION, "kerberos");
			hadoopConfiguration.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHORIZATION, "true");

			sc = new SecurityConfiguration(configuration,
				Collections.singletonList(securityConfig -> new HadoopModule(securityConfig, hadoopConfiguration)));
		} else {
			sc = new SecurityConfiguration(configuration);
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		}

		SecurityUtils.install(sc);

		return SecurityUtils.getInstalledContext();
	}

	public static Configuration loadConfiguration(String workingDirectory, Map<String, String> env) {
		Configuration configuration = GlobalConfiguration.loadConfiguration(workingDirectory);

		final String remoteKeytabPrincipal = env.get(YarnConfigKeys.KEYTAB_PRINCIPAL);

		final String zooKeeperNamespace = env.get(YarnConfigKeys.ENV_ZOOKEEPER_NAMESPACE);

		final Map<String, String> dynamicProperties = FlinkYarnSessionCli.getDynamicProperties(
			env.get(YarnConfigKeys.ENV_DYNAMIC_PROPERTIES));

		final String hostname = env.get(ApplicationConstants.Environment.NM_HOST.key());
		Preconditions.checkState(
			hostname != null,
			"ApplicationMaster hostname variable %s not set",
			ApplicationConstants.Environment.NM_HOST.key());

		configuration.setString(JobManagerOptions.ADDRESS, hostname);

		// TODO: Support port ranges for the AM
//		final String portRange = configuration.getString(
//			ConfigConstants.YARN_APPLICATION_MASTER_PORT,
//			ConfigConstants.DEFAULT_YARN_JOB_MANAGER_PORT);

		for (Map.Entry<String, String> property : dynamicProperties.entrySet()) {
			configuration.setString(property.getKey(), property.getValue());
		}

		if (zooKeeperNamespace != null) {
			configuration.setString(HighAvailabilityOptions.HA_CLUSTER_ID, zooKeeperNamespace);
		}

		// if a web monitor shall be started, set the port to random binding
<<<<<<< HEAD
		if (configuration.getInteger(JobManagerOptions.WEB_PORT.key(), 0) >= 0) {
			configuration.setInteger(JobManagerOptions.WEB_PORT, 0);
=======
		if (configuration.getInteger(WebOptions.PORT, 0) >= 0) {
			configuration.setInteger(WebOptions.PORT, 0);
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		}

		// if the user has set the deprecated YARN-specific config keys, we add the
		// corresponding generic config keys instead. that way, later code needs not
		// deal with deprecated config keys

<<<<<<< HEAD
		BootstrapTools.substituteDeprecatedConfigKey(configuration,
			ConfigConstants.YARN_HEAP_CUTOFF_RATIO,
			ConfigConstants.CONTAINERIZED_HEAP_CUTOFF_RATIO);

		BootstrapTools.substituteDeprecatedConfigKey(configuration,
			ConfigConstants.YARN_HEAP_CUTOFF_MIN,
			ConfigConstants.CONTAINERIZED_HEAP_CUTOFF_MIN);

		BootstrapTools.substituteDeprecatedConfigPrefix(configuration,
			ConfigConstants.YARN_APPLICATION_MASTER_ENV_PREFIX,
			ConfigConstants.CONTAINERIZED_MASTER_ENV_PREFIX);

		BootstrapTools.substituteDeprecatedConfigPrefix(configuration,
			ConfigConstants.YARN_TASK_MANAGER_ENV_PREFIX,
			ConfigConstants.CONTAINERIZED_TASK_MANAGER_ENV_PREFIX);
=======
		BootstrapTools.substituteDeprecatedConfigPrefix(configuration,
			ConfigConstants.YARN_APPLICATION_MASTER_ENV_PREFIX,
			ResourceManagerOptions.CONTAINERIZED_MASTER_ENV_PREFIX);

		BootstrapTools.substituteDeprecatedConfigPrefix(configuration,
			ConfigConstants.YARN_TASK_MANAGER_ENV_PREFIX,
			ResourceManagerOptions.CONTAINERIZED_TASK_MANAGER_ENV_PREFIX);
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d

		final String keytabPath;

		if (env.get(YarnConfigKeys.KEYTAB_PATH) == null) {
			keytabPath = null;
		} else {
			File f = new File(workingDirectory, Utils.KEYTAB_FILE_NAME);
			keytabPath = f.getAbsolutePath();
		}

		if (keytabPath != null && remoteKeytabPrincipal != null) {
			configuration.setString(SecurityOptions.KERBEROS_LOGIN_KEYTAB, keytabPath);
			configuration.setString(SecurityOptions.KERBEROS_LOGIN_PRINCIPAL, remoteKeytabPrincipal);
		}

		return configuration;
	}

	public static void logYarnEnvironmentInformation(Map<String, String> env, Logger log) throws IOException {
		final String yarnClientUsername = env.get(YarnConfigKeys.ENV_HADOOP_USER_NAME);
		Preconditions.checkArgument(
			yarnClientUsername != null,
			"YARN client user name environment variable %s not set",
			YarnConfigKeys.ENV_HADOOP_USER_NAME);

		UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();

		log.info("YARN daemon is running as: {} Yarn client user obtainer: {}",
			currentUser.getShortUserName(), yarnClientUsername);
	}
}
