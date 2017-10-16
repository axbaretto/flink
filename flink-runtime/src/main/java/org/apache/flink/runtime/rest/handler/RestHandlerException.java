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

package org.apache.flink.runtime.rest.handler;

<<<<<<< HEAD
import org.apache.flink.util.FlinkException;

=======
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponseStatus;

/**
 * An exception that is thrown if the failure of a REST operation was detected by a handler.
 */
<<<<<<< HEAD
public class RestHandlerException extends FlinkException {
	private static final long serialVersionUID = -1358206297964070876L;

	private final int responseCode;

	public RestHandlerException(String errorMessage, HttpResponseStatus httpResponseStatus) {
		super(errorMessage);
		this.responseCode = httpResponseStatus.code();
	}

	public RestHandlerException(String errorMessage, HttpResponseStatus httpResponseStatus, Throwable cause) {
		super(errorMessage, cause);
		this.responseCode = httpResponseStatus.code();
=======
public class RestHandlerException extends Exception {
	private static final long serialVersionUID = -1358206297964070876L;

	private final String errorMessage;
	private final int responseCode;

	public RestHandlerException(String errorMessage, HttpResponseStatus httpResponseStatus) {
		this.errorMessage = errorMessage;
		this.responseCode = httpResponseStatus.code();
	}

	public String getErrorMessage() {
		return errorMessage;
>>>>>>> ebaa7b5725a273a7f8726663dbdf235c58ff761d
	}

	public HttpResponseStatus getHttpResponseStatus() {
		return HttpResponseStatus.valueOf(responseCode);
	}
}
