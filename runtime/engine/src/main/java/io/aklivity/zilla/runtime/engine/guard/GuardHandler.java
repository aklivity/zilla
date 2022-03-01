/*
 * Copyright 2021-2022 Aklivity Inc.
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

import java.util.List;

public interface GuardHandler
{
    /*
     * Returns a verifier for the specified roles.
     *
     * @param roles  the roles to verify
     *
     * @return  the verifier identifier
     */
    long verifier(
        List<String> roles);

    /*
     * Authorize the credentials.
     *
     * @param session       the parent session (possibly zero)
     * @param credentials   the trusted credentials
     *
     * @return  the session identifier
     */
    long authorize(
        long session,
        String credentials);

    /*
     * Returns the authorized identity.
     *
     * @param session       the session identifier
     *
     * @return  the authorized identity
     */
    String identity(
        long session);

    /*
     * Returns the session expiration time in UTC milliseconds.
     *
     * @param session       the session identifier
     *
     * @return  the expiration time
     */
    long expiresAt(
        long session);

    /*
     * Returns the session challenge time in UTC milliseconds.
     *
     * @param session       the session identifier
     *
     * @return  the challenge time
     */
    long challengeAt(
        long session);

    /*
     * Returns whether the session allows access.
     *
     * @param session       the session identifier
     * @param verifier      the verifier identifier
     *
     * @return  true if the session allows access, otherwise false
     */
    boolean allows(
        long session,
        long verifier);
}
