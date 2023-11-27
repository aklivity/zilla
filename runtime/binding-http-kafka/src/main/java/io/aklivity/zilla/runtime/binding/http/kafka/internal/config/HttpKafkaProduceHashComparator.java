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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.Comparator;

public class HttpKafkaProduceHashComparator implements Comparator<byte[]>
{
    public int compare(
        byte[] array1,
        byte[] array2)
    {
        int minLength = Math.min(array1.length, array2.length);
        int result = 0;
        for (int i = 0; i < minLength && result == 0; i++)
        {
            result = Byte.compare(array1[i], array2[i]);
        }
        return result != 0 ? result : Integer.compare(array1.length, array2.length);
    }
}
