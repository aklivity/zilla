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
package io.aklivity.zilla.specs.guard.jwt.keys;

import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_ES256;
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_RS256;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class JwtKeysTest
{
    @Test
    public void shouldVerifyKeys()
    {
        assertThat(RFC7515_RS256, not(nullValue()));
        assertThat(RFC7515_ES256, not(nullValue()));
    }
}
