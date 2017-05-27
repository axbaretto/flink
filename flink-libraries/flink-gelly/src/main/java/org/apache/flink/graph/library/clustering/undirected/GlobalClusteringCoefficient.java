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

package org.apache.flink.graph.library.clustering.undirected;

import org.apache.flink.api.java.DataSet;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.GraphAnalyticBase;
import org.apache.flink.graph.asm.dataset.Count;
import org.apache.flink.graph.asm.result.PrintableResult;
import org.apache.flink.graph.library.clustering.undirected.GlobalClusteringCoefficient.Result;
import org.apache.flink.graph.library.metric.undirected.VertexMetrics;
import org.apache.flink.types.CopyableValue;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_DEFAULT;

/**
 * The global clustering coefficient measures the connectedness of a graph.
 * Scores range from 0.0 (no triangles) to 1.0 (complete graph).
 *
 * @param <K> graph ID type
 * @param <VV> vertex value type
 * @param <EV> edge value type
 */
public class GlobalClusteringCoefficient<K extends Comparable<K> & CopyableValue<K>, VV, EV>
extends GraphAnalyticBase<K, VV, EV, Result> {

	private Count<TriangleListing.Result<K>> triangleCount;

	private VertexMetrics<K, VV, EV> vertexMetrics;

	// Optional configuration
	private int littleParallelism = PARALLELISM_DEFAULT;

	/**
	 * Override the parallelism of operators processing small amounts of data.
	 *
	 * @param littleParallelism operator parallelism
	 * @return this
	 */
	public GlobalClusteringCoefficient<K, VV, EV> setLittleParallelism(int littleParallelism) {
		this.littleParallelism = littleParallelism;

		return this;
	}

	/*
	 * Implementation notes:
	 *
	 * The requirement that "K extends CopyableValue<K>" can be removed when
	 *   removed from TriangleListing.
	 */

	@Override
	public GlobalClusteringCoefficient<K, VV, EV> run(Graph<K, VV, EV> input)
			throws Exception {
		super.run(input);

		triangleCount = new Count<>();

		DataSet<TriangleListing.Result<K>> triangles = input
			.run(new TriangleListing<K, VV, EV>()
				.setSortTriangleVertices(false)
				.setLittleParallelism(littleParallelism));

		triangleCount.run(triangles);

		vertexMetrics = new VertexMetrics<K, VV, EV>()
			.setParallelism(littleParallelism);

		input.run(vertexMetrics);

		return this;
	}

	@Override
	public Result getResult() {
		// each triangle is counted from each of the three vertices
		long numberOfTriangles = 3 * triangleCount.getResult();

		return new Result(vertexMetrics.getResult().getNumberOfTriplets(), numberOfTriangles);
	}

	/**
	 * Wraps global clustering coefficient metrics.
	 */
	public static class Result
	implements PrintableResult {
		private long tripletCount;

		private long triangleCount;

		/**
		 * Instantiate an immutable result.
		 *
		 * @param tripletCount triplet count
		 * @param triangleCount triangle count
		 */
		public Result(long tripletCount, long triangleCount) {
			this.tripletCount = tripletCount;
			this.triangleCount = triangleCount;
		}

		/**
		 * Get the number of triplets.
		 *
		 * @return number of triplets
		 */
		public long getNumberOfTriplets() {
			return tripletCount;
		}

		/**
		 * Get the number of triangles.
		 *
		 * @return number of triangles
		 */
		public long getNumberOfTriangles() {
			return triangleCount;
		}

		/**
		 * Get the global clustering coefficient score. This is computed as the
		 * number of closed triplets (triangles) divided by the total number of
		 * triplets.
		 *
		 * <p>A score of {@code Double.NaN} is returned for a graph of isolated vertices
		 * for which both the triangle count and number of neighbors are zero.
		 *
		 * @return global clustering coefficient score
		 */
		public double getGlobalClusteringCoefficientScore() {
			return (tripletCount == 0) ? Double.NaN : triangleCount / (double) tripletCount;
		}

		@Override
		public String toPrintableString() {
			return "triplet count: " + tripletCount
				+ ", triangle count: " + triangleCount
				+ ", global clustering coefficient: " + getGlobalClusteringCoefficientScore();
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder()
				.append(tripletCount)
				.append(triangleCount)
				.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}

			if (obj == this) {
				return true;
			}

			if (obj.getClass() != getClass()) {
				return false;
			}

			Result rhs = (Result) obj;

			return new EqualsBuilder()
				.append(tripletCount, rhs.tripletCount)
				.append(triangleCount, rhs.triangleCount)
				.isEquals();
		}
	}
}
