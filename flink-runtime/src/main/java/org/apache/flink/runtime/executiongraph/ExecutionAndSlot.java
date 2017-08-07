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

package org.apache.flink.runtime.executiongraph;

import org.apache.flink.runtime.instance.SimpleSlot;

import java.util.concurrent.CompletableFuture;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A pair of an {@link Execution} together with a slot future.
 */
public class ExecutionAndSlot {

	public final Execution executionAttempt;

	public final CompletableFuture<SimpleSlot> slotFuture;

	public ExecutionAndSlot(Execution executionAttempt, CompletableFuture<SimpleSlot> slotFuture) {
		this.executionAttempt = checkNotNull(executionAttempt);
		this.slotFuture = checkNotNull(slotFuture);
	}

	// -----------------------------------------------------------------------

	@Override
	public String toString() {
		return super.toString();
	}
}
