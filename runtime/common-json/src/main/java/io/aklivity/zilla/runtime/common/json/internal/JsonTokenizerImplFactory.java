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
package io.aklivity.zilla.runtime.common.json.internal;

import io.aklivity.zilla.runtime.common.json.JsonTokenizer;
import io.aklivity.zilla.runtime.common.json.JsonTokenizerFactory;

// Always-available baseline: priority 0 so any other registered factory that reports itself available wins.
public final class JsonTokenizerImplFactory implements JsonTokenizerFactory
{
    @Override
    public JsonTokenizer create(
        int maxValueSize,
        boolean quoted)
    {
        return new JsonTokenizerImpl(maxValueSize, quoted);
    }

    @Override
    public int priority()
    {
        return 0;
    }

    @Override
    public boolean available()
    {
        return true;
    }
}
