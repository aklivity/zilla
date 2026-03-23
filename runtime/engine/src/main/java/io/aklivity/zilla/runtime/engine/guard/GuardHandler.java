/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.guard;

/**
 * Manages authorization sessions for streams passing through a guarded binding.
 * <p>
 * A {@code GuardHandler} is obtained from {@link GuardContext#attach(GuardConfig)} and is
 * confined to a single I/O thread. It maintains the set of currently active sessions and
 * evaluates credential validity on the hot path without blocking or synchronization.
 * </p>
 * <p>
 * Sessions are identified by an opaque {@code long} session id returned by
 * {@link #reauthorize}. A session id of {@link #NOT_AUTHORIZED} indicates failure.
 * </p>
 *
 * @see GuardContext
 */
public interface GuardHandler
{
    /** Sentinel session id indicating that authorization failed or is not valid. */
    long NOT_AUTHORIZED = 0L;

    /** Sentinel expiry time indicating a session that never expires. */
    long EXPIRES_NEVER = Long.MAX_VALUE;

    /**
     * Validates the given credentials and returns a session id for the authorized session.
     * <p>
     * If the credentials are valid, returns a positive session id that can be used to query
     * session state. If authorization fails, returns {@link #NOT_AUTHORIZED}.
     * </p>
     *
     * @param traceId    the trace identifier for diagnostics
     * @param bindingId  the binding identifier requesting authorization
     * @param contextId  a context identifier (e.g., connection id), or {@code 0} if none
     * @param credentials  the raw credential string (e.g., a JWT bearer token)
     * @return a positive session id if authorized, or {@link #NOT_AUTHORIZED} on failure
     */
    long reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials);

    /**
     * Invalidates and releases the given session.
     *
     * @param sessionId  the session identifier to deauthorize
     */
    void deauthorize(
        long sessionId);

    /**
     * Returns the identity string associated with an authorized session
     * (e.g., the JWT {@code sub} claim).
     *
     * @param sessionId  the session identifier
     * @return the identity string, or {@code null} if the session is not recognized
     */
    String identity(
        long sessionId);

    /**
     * Returns the value of a named attribute for an authorized session
     * (e.g., a custom JWT claim).
     *
     * @param sessionId  the session identifier
     * @param name       the attribute name
     * @return the attribute value, or {@code null} if not present
     */
    String attribute(
        long sessionId,
        String name);

    /**
     * Returns the raw credential string for an authorized session
     * (e.g., the original JWT bearer token).
     *
     * @param sessionId  the session identifier
     * @return the credential string
     */
    String credentials(
        long sessionId);

    /**
     * Returns the UTC millisecond timestamp at which this session expires and must be
     * re-authorized. Returns {@link #EXPIRES_NEVER} for sessions with no expiry.
     *
     * @param sessionId  the session identifier
     * @return the expiration time in UTC milliseconds
     */
    long expiresAt(
        long sessionId);

    /**
     * Returns the UTC millisecond timestamp at which this session should next be challenged.
     * <p>
     * This may be earlier than {@link #expiresAt} when a challenge window is configured,
     * allowing proactive re-authorization before the session actually expires.
     * Falls back to {@link #expiresAt} if no challenge window is configured.
     * </p>
     *
     * @param sessionId  the session identifier
     * @return the challenge time in UTC milliseconds
     */
    long expiringAt(
        long sessionId);

    /**
     * Returns {@code true} if the session should be challenged at the given time, and records
     * that the challenge was issued.
     * <p>
     * The guard handler may assume a challenge was sent if this method returns {@code true},
     * and will not return {@code true} again for the same session until conditions change.
     * </p>
     *
     * @param sessionId  the session identifier
     * @param now        the current time in UTC milliseconds
     * @return {@code true} if a challenge should be sent now, {@code false} otherwise
     */
    boolean challenge(
        long sessionId,
        long now);
}
