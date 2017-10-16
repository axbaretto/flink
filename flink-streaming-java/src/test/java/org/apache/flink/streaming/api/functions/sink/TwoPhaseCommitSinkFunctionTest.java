/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.functions.sink;

import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.base.StringSerializer;
<<<<<<< HEAD
import org.apache.flink.api.java.tuple.Tuple2;
=======
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import org.apache.flink.streaming.api.operators.StreamSink;
import org.apache.flink.streaming.runtime.tasks.OperatorStateHandles;
import org.apache.flink.streaming.util.OneInputStreamOperatorTestHarness;

<<<<<<< HEAD
=======
import org.junit.After;
import org.junit.Before;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
<<<<<<< HEAD
import java.io.Writer;
=======
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
<<<<<<< HEAD
=======
import static org.junit.Assert.fail;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d

/**
 * Tests for {@link TwoPhaseCommitSinkFunction}.
 */
public class TwoPhaseCommitSinkFunctionTest {
<<<<<<< HEAD
	@Test
	public void testNotifyOfCompletedCheckpoint() throws Exception {
		File tmpDirectory = Files.createTempDirectory(this.getClass().getSimpleName() + "_tmp").toFile();
		File targetDirectory = Files.createTempDirectory(this.getClass().getSimpleName() + "_target").toFile();
		OneInputStreamOperatorTestHarness<String, Object> testHarness = createTestHarness(tmpDirectory, targetDirectory);

		testHarness.setup();
		testHarness.open();
		testHarness.processElement("42", 0);
		testHarness.snapshot(0, 1);
		testHarness.processElement("43", 2);
		testHarness.snapshot(1, 3);
		testHarness.processElement("44", 4);
		testHarness.snapshot(2, 5);
		testHarness.notifyOfCompletedCheckpoint(1);

		assertExactlyOnceForDirectory(targetDirectory, Arrays.asList("42", "43"));
		assertEquals(2, tmpDirectory.listFiles().length); // one for checkpointId 2 and second for the currentTransaction
		testHarness.close();
	}

	public OneInputStreamOperatorTestHarness<String, Object> createTestHarness(File tmpDirectory, File targetDirectory) throws Exception {
		tmpDirectory.deleteOnExit();
		targetDirectory.deleteOnExit();
		FileBasedSinkFunction sinkFunction = new FileBasedSinkFunction(tmpDirectory, targetDirectory);
		return new OneInputStreamOperatorTestHarness<>(new StreamSink<>(sinkFunction), StringSerializer.INSTANCE);
=======
	TestContext context;

	@Before
	public void setUp() throws Exception {
		context = new TestContext();
	}

	@After
	public void tearDown() throws Exception {
		context.close();
	}

	@Test
	public void testNotifyOfCompletedCheckpoint() throws Exception {
		context.harness.open();
		context.harness.processElement("42", 0);
		context.harness.snapshot(0, 1);
		context.harness.processElement("43", 2);
		context.harness.snapshot(1, 3);
		context.harness.processElement("44", 4);
		context.harness.snapshot(2, 5);
		context.harness.notifyOfCompletedCheckpoint(1);

		assertExactlyOnceForDirectory(context.targetDirectory, Arrays.asList("42", "43"));
		assertEquals(2, context.tmpDirectory.listFiles().length); // one for checkpointId 2 and second for the currentTransaction
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
	}

	@Test
	public void testFailBeforeNotify() throws Exception {
<<<<<<< HEAD
		File tmpDirectory = Files.createTempDirectory(this.getClass().getSimpleName() + "_tmp").toFile();
		File targetDirectory = Files.createTempDirectory(this.getClass().getSimpleName() + "_target").toFile();
		OneInputStreamOperatorTestHarness<String, Object> testHarness = createTestHarness(tmpDirectory, targetDirectory);

		testHarness.setup();
		testHarness.open();
		testHarness.processElement("42", 0);
		testHarness.snapshot(0, 1);
		testHarness.processElement("43", 2);
		OperatorStateHandles snapshot = testHarness.snapshot(1, 3);

		assertTrue(tmpDirectory.setWritable(false));
		try {
			testHarness.processElement("44", 4);
			testHarness.snapshot(2, 5);
=======
		context.harness.open();
		context.harness.processElement("42", 0);
		context.harness.snapshot(0, 1);
		context.harness.processElement("43", 2);
		OperatorStateHandles snapshot = context.harness.snapshot(1, 3);

		assertTrue(context.tmpDirectory.setWritable(false));
		try {
			context.harness.processElement("44", 4);
			context.harness.snapshot(2, 5);
			fail("something should fail");
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		}
		catch (Exception ex) {
			if (!(ex.getCause() instanceof FileNotFoundException)) {
				throw ex;
			}
			// ignore
		}
<<<<<<< HEAD
		testHarness.close();

		assertTrue(tmpDirectory.setWritable(true));

		testHarness = createTestHarness(tmpDirectory, targetDirectory);
		testHarness.setup();
		testHarness.initializeState(snapshot);
		testHarness.close();

		assertExactlyOnceForDirectory(targetDirectory, Arrays.asList("42", "43"));
		assertEquals(0, tmpDirectory.listFiles().length);
=======
		context.close();

		assertTrue(context.tmpDirectory.setWritable(true));

		context.open();
		context.harness.initializeState(snapshot);

		assertExactlyOnceForDirectory(context.targetDirectory, Arrays.asList("42", "43"));
		context.close();

		assertEquals(0, context.tmpDirectory.listFiles().length);
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
	}

	private void assertExactlyOnceForDirectory(File targetDirectory, List<String> expectedValues) throws IOException {
		ArrayList<String> actualValues = new ArrayList<>();
		for (File file : targetDirectory.listFiles()) {
			actualValues.addAll(Files.readAllLines(file.toPath(), Charset.defaultCharset()));
		}
		Collections.sort(actualValues);
		Collections.sort(expectedValues);
		assertEquals(expectedValues, actualValues);
	}

<<<<<<< HEAD
	private static class FileBasedSinkFunction extends TwoPhaseCommitSinkFunction<String, FileTransaction> {
=======
	private static class FileBasedSinkFunction extends TwoPhaseCommitSinkFunction<String, FileTransaction, Void> {
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
		private final File tmpDirectory;
		private final File targetDirectory;

		public FileBasedSinkFunction(File tmpDirectory, File targetDirectory) {
<<<<<<< HEAD
			super(
				TypeInformation.of(FileTransaction.class),
				TypeInformation.of(new TypeHint<List<Tuple2<Long, FileTransaction>>>() {}));
=======
			super(TypeInformation.of(new TypeHint<State<FileTransaction, Void>>() {}));
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d

			if (!tmpDirectory.isDirectory() || !targetDirectory.isDirectory()) {
				throw new IllegalArgumentException();
			}

			this.tmpDirectory = tmpDirectory;
			this.targetDirectory = targetDirectory;
		}

		@Override
		protected void invoke(FileTransaction transaction, String value) throws Exception {
			transaction.writer.write(value);
		}

		@Override
		protected FileTransaction beginTransaction() throws Exception {
			File tmpFile = new File(tmpDirectory, UUID.randomUUID().toString());
			return new FileTransaction(tmpFile);
		}

		@Override
		protected void preCommit(FileTransaction transaction) throws Exception {
			transaction.writer.flush();
			transaction.writer.close();
		}

		@Override
		protected void commit(FileTransaction transaction) {
			try {
<<<<<<< HEAD
				Files.move(transaction.tmpFile.toPath(), new File(targetDirectory, transaction.tmpFile.getName()).toPath(), ATOMIC_MOVE);
=======
				Files.move(
					transaction.tmpFile.toPath(),
					new File(targetDirectory, transaction.tmpFile.getName()).toPath(),
					ATOMIC_MOVE);
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		protected void abort(FileTransaction transaction) {
			try {
				transaction.writer.close();
			} catch (IOException e) {
				// ignore
			}
			transaction.tmpFile.delete();
		}

		@Override
		protected void recoverAndAbort(FileTransaction transaction) {
			transaction.tmpFile.delete();
		}
	}

	private static class FileTransaction {
		private final File tmpFile;
<<<<<<< HEAD
		private final transient Writer writer;
=======
		private final transient BufferedWriter writer;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d

		public FileTransaction(File tmpFile) throws IOException {
			this.tmpFile = tmpFile;
			this.writer = new BufferedWriter(new FileWriter(tmpFile));
		}
<<<<<<< HEAD
=======

		@Override
		public String toString() {
			return String.format("FileTransaction[%s]", tmpFile.getName());
		}
	}

	private static class TestContext implements AutoCloseable {
		public final File tmpDirectory = Files.createTempDirectory(TwoPhaseCommitSinkFunctionTest.class.getSimpleName() + "_tmp").toFile();
		public final File targetDirectory = Files.createTempDirectory(TwoPhaseCommitSinkFunctionTest.class.getSimpleName() + "_target").toFile();

		public FileBasedSinkFunction sinkFunction;
		public OneInputStreamOperatorTestHarness<String, Object> harness;

		private TestContext() throws Exception {
			tmpDirectory.deleteOnExit();
			targetDirectory.deleteOnExit();
			open();
		}

		@Override
		public void close() throws Exception {
			harness.close();
		}

		public void open() throws Exception {
			sinkFunction = new FileBasedSinkFunction(tmpDirectory, targetDirectory);
			harness = new OneInputStreamOperatorTestHarness<>(new StreamSink<>(sinkFunction), StringSerializer.INSTANCE);
			harness.setup();
		}
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
	}
}
