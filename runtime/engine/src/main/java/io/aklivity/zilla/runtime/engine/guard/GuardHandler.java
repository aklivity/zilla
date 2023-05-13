/*
 * Copyright 2021-2023 Aklivity Inc.
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

public interface GuardHandler
{
    long NOT_AUTHORIZED = 0L;

    long EXPIRES_NEVER = Long.MAX_VALUE;

    /*
     * Reauthorize the credentials.
     *
     * @param contextId     the context identifier, zero if no context
     * @param credentials   the trusted credentials
     *
     * @return  the session identifier
     */
    long reauthorize(
        long contextId,
        String credentials);

    /*
     * Deauthorizes the session.
     *
     * @param sessionId     the session identifier
     */
    void deauthorize(
        long sessionId);

    /*
     * Returns the authorized identity.
     *
     * @param sessionId     the session identifier
     *
     * @return  the authorized identity
     */
    String identity(
        long sessionId);

    /*
     * Returns the session expiration time in UTC milliseconds.
     *
     * @param sessionId     the session identifier
     *
     * @return  the expiration time
     */
    long expiresAt(
        long sessionId);

    /*
     * Returns the session expiring time in UTC milliseconds.
     *
     * This may be the challenge time if specified, or the expiration time if challenge duration is unset.
     *
     * @param sessionId     the session identifier
     *
     * @return  the challenge time
     */
    long expiringAt(
        long sessionId);

    /*
     * Returns true if should challenge now, otherwise false.
     *
     * The guard handler may assume that a challenge was sent if the return value is true.
     *
     * @param sessionId     the session identifier
     * @param now           the current time in UTC milliseconds
     *
     * @return  true if should challenge now, otherwise false
     */
    boolean challenge(
        long sessionId,
        long now);
}
