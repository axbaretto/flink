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

package org.apache.flink.runtime.blob;

import org.apache.flink.api.common.JobID;
import org.apache.flink.util.OperatingSystem;
import org.apache.flink.util.TestLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
<<<<<<< HEAD

/**
 * Tests for {@link BlobUtils}.
 */
=======
import static org.mockito.Mockito.mock;

>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
public class BlobUtilsTest extends TestLogger {

	private static final String CANNOT_CREATE_THIS = "cannot-create-this";

	private File blobUtilsTestDirectory;

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void before() throws IOException {
		assumeTrue(!OperatingSystem.isWindows()); //setWritable doesn't work on Windows.

		// Prepare test directory
		blobUtilsTestDirectory = temporaryFolder.newFolder();
		assertTrue(blobUtilsTestDirectory.setExecutable(true, false));
		assertTrue(blobUtilsTestDirectory.setReadable(true, false));
		assertTrue(blobUtilsTestDirectory.setWritable(false, false));
	}

	@After
	public void after() {
		// Cleanup test directory, ensure it was empty
		assertTrue(blobUtilsTestDirectory.delete());
	}

	@Test(expected = IOException.class)
	public void testExceptionOnCreateStorageDirectoryFailure() throws
		IOException {
		// Should throw an Exception
		BlobUtils.initLocalStorageDirectory(new File(blobUtilsTestDirectory, CANNOT_CREATE_THIS).getAbsolutePath());
	}

<<<<<<< HEAD
	@Test(expected = IOException.class)
	public void testExceptionOnCreateCacheDirectoryFailureNoJob() throws IOException {
		// Should throw an Exception
		BlobUtils.getStorageLocation(new File(blobUtilsTestDirectory, CANNOT_CREATE_THIS), null, new TransientBlobKey());
	}

	@Test(expected = IOException.class)
	public void testExceptionOnCreateCacheDirectoryFailureForJobTransient() throws IOException {
		// Should throw an Exception
		BlobUtils.getStorageLocation(new File(blobUtilsTestDirectory, CANNOT_CREATE_THIS), new JobID(), new TransientBlobKey());
	}

	@Test(expected = IOException.class)
	public void testExceptionOnCreateCacheDirectoryFailureForJobPermanent() throws IOException {
		// Should throw an Exception
		BlobUtils.getStorageLocation(new File(blobUtilsTestDirectory, CANNOT_CREATE_THIS), new JobID(), new PermanentBlobKey());
=======
	@Test(expected = Exception.class)
	public void testExceptionOnCreateCacheDirectoryFailureNoJob() {
		// Should throw an Exception
		BlobUtils.getStorageLocation(new File(blobUtilsTestDirectory, CANNOT_CREATE_THIS), null, mock(BlobKey.class));
	}

	@Test(expected = Exception.class)
	public void testExceptionOnCreateCacheDirectoryFailureForJob() {
		// Should throw an Exception
		BlobUtils.getStorageLocation(new File(blobUtilsTestDirectory, CANNOT_CREATE_THIS), new JobID(), mock(BlobKey.class));
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
	}
}
