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

package org.apache.flink.runtime.instance;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.jobmanager.scheduler.ScheduledUnit;
import org.apache.flink.runtime.jobmanager.slots.AllocatedSlot;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.resourcemanager.ResourceManagerGateway;
import org.apache.flink.runtime.rpc.RpcGateway;
import org.apache.flink.runtime.rpc.RpcTimeout;
import org.apache.flink.runtime.taskexecutor.slot.SlotOffer;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;
import org.apache.flink.util.AbstractID;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * The gateway for calls on the {@link SlotPool}. 
 */
public interface SlotPoolGateway extends RpcGateway {

	// ------------------------------------------------------------------------
	//  shutdown
	// ------------------------------------------------------------------------

	void suspend();

	// ------------------------------------------------------------------------
	//  resource manager connection
	// ------------------------------------------------------------------------

	/**
	 * Connects the SlotPool to the given ResourceManager. After this method is called, the
	 * SlotPool will be able to request resources from the given ResourceManager.
	 * 
	 * @param resourceManagerGateway  The RPC gateway for the resource manager.
	 */
	void connectToResourceManager(ResourceManagerGateway resourceManagerGateway);

	/**
	 * Disconnects the slot pool from its current Resource Manager. After this call, the pool will not
	 * be able to request further slots from the Resource Manager, and all currently pending requests
	 * to the resource manager will be canceled.
	 * 
	 * <p>The slot pool will still be able to serve slots from its internal pool.
	 */
	void disconnectResourceManager();

	// ------------------------------------------------------------------------
	//  registering / un-registering TaskManagers and slots
	// ------------------------------------------------------------------------

	CompletableFuture<Acknowledge> registerTaskManager(ResourceID resourceID);

	CompletableFuture<Acknowledge> releaseTaskManager(ResourceID resourceID);

	CompletableFuture<Boolean> offerSlot(AllocatedSlot slot);

	CompletableFuture<Collection<SlotOffer>> offerSlots(Collection<Tuple2<AllocatedSlot, SlotOffer>> offers);
	
	void failAllocation(AllocationID allocationID, Exception cause);

	// ------------------------------------------------------------------------
	//  allocating and disposing slots
	// ------------------------------------------------------------------------

	CompletableFuture<SimpleSlot> allocateSlot(
			SlotRequestID requestId,
			ScheduledUnit task,
			ResourceProfile resources,
			Iterable<TaskManagerLocation> locationPreferences,
			@RpcTimeout Time timeout);

	void returnAllocatedSlot(Slot slot);

	/**
	 * Cancel a slot allocation request.
	 *
	 * @param requestId identifying the slot allocation request
	 * @return Future acknowledge if the slot allocation has been cancelled
	 */
	CompletableFuture<Acknowledge> cancelSlotAllocation(SlotRequestID requestId);

	/**
	 * Request ID identifying different slot requests.
	 */
	final class SlotRequestID extends AbstractID {
		private static final long serialVersionUID = -6072105912250154283L;
	}
}
