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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.ChannelConfig;

public interface ZillaChannelConfig extends ChannelConfig
{
    void setTransmission(ZillaTransmission transmission);

    ZillaTransmission getTransmission();

    void setWindow(int window);

    int getWindow();

    void setSharedWindow(int sharedWindow);

    int getSharedWindow();

    void setBudgetId(long budgetId);

    long getBudgetId();

    void setPadding(int padding);

    int getPadding();

    void setUpdate(ZillaUpdateMode update);

    ZillaUpdateMode getUpdate();

    void setThrottle(ZillaThrottleMode throttle);

    ZillaThrottleMode getThrottle();

    boolean hasThrottle();

    void setAffinity(long affinity);

    long getAffinity();

    void setCapabilities(byte capabilities);

    byte getCapabilities();
}
