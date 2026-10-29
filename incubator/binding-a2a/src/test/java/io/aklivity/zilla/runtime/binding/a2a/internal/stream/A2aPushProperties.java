/*
 * Copyright 2021-2026 Aklivity Inc
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

package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures.ACTIVE_KEY;
import static io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures.BASE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures.CONFIG_URL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures.HOOK_URL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures.NONCE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures.RETIRED_KEY;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.aklivity.k3po.runtime.junit.rules.K3poRule;

// k3po copies script properties when its rule is applied, so a per-test sealed token has to be supplied by a
// rule applied ahead of it (inner to it in the chain), selected by the test's @A2aPushToken variant
final class A2aPushProperties implements TestRule
{
    private final K3poRule k3po;
    private final boolean network;

    A2aPushProperties(
        K3poRule k3po,
        boolean network)
    {
        this.k3po = k3po;
        this.network = network;
    }

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        final A2aPushToken token = description.getAnnotation(A2aPushToken.class);

        if (token != null)
        {
            switch (token.value())
            {
            case CONFIG:
                k3po.scriptProperty("sealedUrl \"" + BASE + "/" + A2aPushFixtures.token(CONFIG_URL) + "\"");
                k3po.scriptProperty("nonce \"" + NONCE + "\"");
                break;
            case VALID:
                notify(A2aPushFixtures.token(HOOK_URL), NONCE, "t-1");
                break;
            case MISSING_NONCE:
                k3po.scriptProperty(network ? "path \"/a2a/push/" + A2aPushFixtures.token(HOOK_URL) + "\""
                    : "sealed \"" + A2aPushFixtures.token(HOOK_URL) + "\"");
                k3po.scriptProperty("taskId \"t-1\"");
                break;
            case TAMPERED:
                notify(A2aPushFixtures.tampered(A2aPushFixtures.token(HOOK_URL)), NONCE, "t-1");
                break;
            case GARBLED:
                notify("garbled", NONCE, "t-1");
                break;
            case EXPIRED:
                notify(A2aPushFixtures.token(ACTIVE_KEY, HOOK_URL, "t-1", -1L), NONCE, "t-1");
                break;
            case WRONG_NONCE:
                notify(A2aPushFixtures.token(HOOK_URL), "wrong-nonce", "t-1");
                break;
            case TASK_MISMATCH:
                notify(A2aPushFixtures.token(HOOK_URL), NONCE, "t-9");
                break;
            case RETIRED_KEY:
                notify(A2aPushFixtures.token(RETIRED_KEY, HOOK_URL, "t-1", 3600L), NONCE, "t-1");
                break;
            case BLOCKED_TARGET:
                notify(A2aPushFixtures.token("https://client.example.org/hooks/1"), NONCE, "t-1");
                break;
            }
        }

        return base;
    }

    private void notify(
        String sealed,
        String token,
        String taskId)
    {
        k3po.scriptProperty(network ? "path \"/a2a/push/" + sealed + "\"" : "sealed \"" + sealed + "\"");
        k3po.scriptProperty("token \"" + token + "\"");
        k3po.scriptProperty("taskId \"" + taskId + "\"");
    }
}
