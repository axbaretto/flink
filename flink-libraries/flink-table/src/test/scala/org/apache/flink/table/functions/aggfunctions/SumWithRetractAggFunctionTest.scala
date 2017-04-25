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

package org.apache.flink.table.functions.aggfunctions

import java.math.BigDecimal
import org.apache.flink.table.functions.AggregateFunction

/**
  * Test case for built-in sum with retract aggregate function
  *
  * @tparam T the type for the aggregation result
  */
abstract class SumWithRetractAggFunctionTestBase[T: Numeric]
  extends AggFunctionTestBase[T, SumWithRetractAccumulator[T]] {

  private val numeric: Numeric[T] = implicitly[Numeric[T]]

  def maxVal: T

  private val minVal = numeric.negate(maxVal)

  override def inputValueSets: Seq[Seq[T]] = Seq(
    Seq(
      minVal,
      numeric.fromInt(1),
      null.asInstanceOf[T],
      numeric.fromInt(2),
      numeric.fromInt(3),
      numeric.fromInt(4),
      numeric.fromInt(5),
      numeric.fromInt(-10),
      numeric.fromInt(-20),
      numeric.fromInt(17),
      null.asInstanceOf[T],
      maxVal
    ),
    Seq(
      null.asInstanceOf[T],
      null.asInstanceOf[T],
      null.asInstanceOf[T],
      null.asInstanceOf[T],
      null.asInstanceOf[T],
      null.asInstanceOf[T]
    )
  )

  override def expectedResults: Seq[T] = Seq(
    numeric.fromInt(2),
    null.asInstanceOf[T]
  )

  override def retractFunc = aggregator.getClass.getMethod("retract", accType, classOf[Any])
}

class ByteSumWithRetractAggFunctionTest extends SumWithRetractAggFunctionTestBase[Byte] {

  override def maxVal = (Byte.MaxValue / 2).toByte

  override def aggregator: AggregateFunction[Byte, SumWithRetractAccumulator[Byte]] =
    new ByteSumWithRetractAggFunction
}

class ShortSumWithRetractAggFunctionTest extends SumWithRetractAggFunctionTestBase[Short] {

  override def maxVal = (Short.MaxValue / 2).toShort

  override def aggregator: AggregateFunction[Short, SumWithRetractAccumulator[Short]] =
    new ShortSumWithRetractAggFunction
}

class IntSumWithRetractAggFunctionTest extends SumWithRetractAggFunctionTestBase[Int] {

  override def maxVal = Int.MaxValue / 2

  override def aggregator: AggregateFunction[Int, SumWithRetractAccumulator[Int]] =
    new IntSumWithRetractAggFunction
}

class LongSumWithRetractAggFunctionTest extends SumWithRetractAggFunctionTestBase[Long] {

  override def maxVal = Long.MaxValue / 2

  override def aggregator: AggregateFunction[Long, SumWithRetractAccumulator[Long]] =
    new LongSumWithRetractAggFunction
}

class FloatSumWithRetractAggFunctionTest extends SumWithRetractAggFunctionTestBase[Float] {

  override def maxVal = 12345.6789f

  override def aggregator: AggregateFunction[Float, SumWithRetractAccumulator[Float]] =
    new FloatSumWithRetractAggFunction
}

class DoubleSumWithRetractAggFunctionTest extends SumWithRetractAggFunctionTestBase[Double] {

  override def maxVal = 12345.6789d

  override def aggregator: AggregateFunction[Double, SumWithRetractAccumulator[Double]] =
    new DoubleSumWithRetractAggFunction
}


class DecimalSumWithRetractAggFunctionTest
  extends AggFunctionTestBase[BigDecimal, DecimalSumWithRetractAccumulator] {

  override def inputValueSets: Seq[Seq[_]] = Seq(
    Seq(
      new BigDecimal("1"),
      new BigDecimal("2"),
      new BigDecimal("3"),
      null,
      new BigDecimal("0"),
      new BigDecimal("-1000"),
      new BigDecimal("0.000000000002"),
      new BigDecimal("1000"),
      new BigDecimal("-0.000000000001"),
      new BigDecimal("999.999"),
      null,
      new BigDecimal("4"),
      new BigDecimal("-999.999"),
      null
    ),
    Seq(
      null,
      null,
      null,
      null,
      null
    )
  )

  override def expectedResults: Seq[BigDecimal] = Seq(
    new BigDecimal("10.000000000001"),
    null
  )

  override def aggregator: AggregateFunction[BigDecimal, DecimalSumWithRetractAccumulator] =
    new DecimalSumWithRetractAggFunction()

  override def retractFunc = aggregator.getClass.getMethod("retract", accType, classOf[Any])
}


