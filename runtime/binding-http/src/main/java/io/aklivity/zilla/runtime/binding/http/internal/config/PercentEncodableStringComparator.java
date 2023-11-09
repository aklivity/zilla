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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLDecoder;
import java.util.Comparator;

public class PercentEncodableStringComparator implements Comparator<String>
{
    @Override
    public int compare(
        String input1,
        String input2)
    {
        String decoded1 = URLDecoder.decode(input1, UTF_8);
        String decoded2 = URLDecoder.decode(input2, UTF_8);
        return decoded1.compareTo(decoded2);
    }
}
