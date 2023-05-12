/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.guard.jwt.internal.keys;

import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtKeyConfig;

public final class JwtKeyConfigs
{
    public static final JwtKeyConfig RFC7515_RS256_CONFIG;
    public static final JwtKeyConfig RFC7515_ES256_CONFIG;

    static
    {
        // RFC 7515, section A.2.1
        RFC7515_RS256_CONFIG = new JwtKeyConfig(
                "RSA",
                "test",
                "verify",
                "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddx" +
                "HmfHQp-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMs" +
                "D1W_YpRPEwOWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSH" +
                "SXndS5z5rexMdbBYUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdV" +
                "MTBQY4uDZlxvb3qCo5ZwKh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8" +
                "NAco9_h9iaGNj8q2ethFkMLs91kzk2PAcDTW9gb54h4FRWyuXpoQ",
                "AQAB",
                "RS256",
                null,
                null,
                null);

        // RFC 7515, section A.3.1
        RFC7515_ES256_CONFIG = new JwtKeyConfig(
                "RSA",
                "test",
                "verify",
                null,
                null,
                null,
                "P-256",
                "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0");
    }

    private JwtKeyConfigs()
    {
    }
}
