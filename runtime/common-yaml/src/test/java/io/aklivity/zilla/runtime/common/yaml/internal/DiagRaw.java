/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.yaml.internal;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class DiagRaw
{
    @Test
    void measure() throws Exception
    {
        URL res = DiagRaw.class.getResource("/io/aklivity/zilla/runtime/common/yaml/data-2022-01-17");
        Path suite = Path.of(res.toURI());
        int[] total = {0};
        int[] rawOk = {0};
        Map<String, Integer> bail = new TreeMap<>();
        Files.find(suite, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent).filter(p -> !Files.exists(p.resolve("error")))
            .sorted(Comparator.comparing(p -> suite.relativize(p).toString()))
            .forEach(dir ->
            {
                try
                {
                    String t = Files.readString(dir.resolve("in.yaml"));
                    total[0]++;
                    if (new YamlStreamScanner().scan(t, true))
                    {
                        rawOk[0]++;
                    }
                    else
                    {
                        bail.merge(cat(t), 1, Integer::sum);
                    }
                }
                catch (Exception ex)
                {
                    bail.merge("EXC:" + ex.getClass().getSimpleName(), 1, Integer::sum);
                }
            });
        System.out.println("=== total=" + total[0] + " rawOk=" + rawOk[0] + " ===");
        bail.forEach((k, v) -> System.out.println(v + "\t" + k));
    }

    private static String cat(String t)
    {
        if (t.indexOf('\t') != -1)
        {
            return "tab";
        }
        if (t.lines().anyMatch(l -> l.startsWith("--- ") && l.length() > 4))
        {
            return "inline-marker";
        }
        if (t.contains("%TAG"))
        {
            return "%TAG";
        }
        if (t.lines().anyMatch(l -> l.strip().startsWith("{") || l.strip().startsWith("[")))
        {
            return "flow-multiline";
        }
        if (t.lines().anyMatch(l -> l.strip().startsWith("? ") || l.strip().equals("?")))
        {
            return "explicit-key";
        }
        if (t.lines().anyMatch(l -> l.contains("&") || l.contains("*")))
        {
            return "anchor/alias";
        }
        if (t.lines().anyMatch(l -> l.contains("!")))
        {
            return "tag";
        }
        return "other";
    }
}
