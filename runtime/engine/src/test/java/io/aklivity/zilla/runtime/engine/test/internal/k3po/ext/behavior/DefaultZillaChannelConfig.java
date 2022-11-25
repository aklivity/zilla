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

import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaChannel.NATIVE_BUFFER_FACTORY;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaThrottleMode.NONE;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaTransmission.SIMPLEX;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_AFFINITY;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_BYTE_ORDER;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_CAPABILITIES;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_PADDING;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_SHARED_WINDOW;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_STREAM_ID;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_THROTTLE;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_TRANSMISSION;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_UPDATE;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.ZillaTypeSystem.OPTION_WINDOW;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.util.Conversions.convertToByte;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.util.Conversions.convertToInt;
import static io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.util.Conversions.convertToLong;

import java.util.Objects;

import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.DefaultChannelConfig;

public class DefaultZillaChannelConfig extends DefaultChannelConfig implements ZillaChannelConfig
{
    private ZillaTransmission transmission = SIMPLEX;
    private int window;
    private int sharedWindow;
    private long budgetId;
    private long streamId;
    private int padding;
    private ZillaThrottleMode throttle = ZillaThrottleMode.STREAM;
    private ZillaUpdateMode update = ZillaUpdateMode.STREAM;
    private long affinity;
    private byte capabilities;

    public DefaultZillaChannelConfig()
    {
        super();
        setBufferFactory(NATIVE_BUFFER_FACTORY);
    }

    @Override
    public void setTransmission(
        ZillaTransmission transmission)
    {
        this.transmission = transmission;
    }

    @Override
    public ZillaTransmission getTransmission()
    {
        return transmission;
    }

    @Override
    public void setWindow(int window)
    {
        this.window = window;
    }

    @Override
    public int getWindow()
    {
        return window;
    }

    @Override
    public void setSharedWindow(int sharedWindow)
    {
        this.sharedWindow = sharedWindow;
    }

    @Override
    public int getSharedWindow()
    {
        return sharedWindow;
    }

    @Override
    public void setBudgetId(
        long budgetId)
    {
        this.budgetId = budgetId;
    }

    @Override
    public long getBudgetId()
    {
        return budgetId;
    }

    @Override
    public void setStreamId(
        long streamId)
    {
        this.streamId = streamId;
    }

    @Override
    public long getStreamId()
    {
        return streamId;
    }

    @Override
    public void setPadding(int padding)
    {
        this.padding = padding;
    }

    @Override
    public int getPadding()
    {
        return padding;
    }

    @Override
    public void setUpdate(
        ZillaUpdateMode update)
    {
        this.update = update;
    }

    @Override
    public ZillaUpdateMode getUpdate()
    {
        return update;
    }

    @Override
    public void setThrottle(
        ZillaThrottleMode throttle)
    {
        this.throttle = throttle;
    }

    @Override
    public ZillaThrottleMode getThrottle()
    {
        return throttle;
    }

    @Override
    public boolean hasThrottle()
    {
        return throttle != NONE;
    }

    @Override
    public void setAffinity(
        long affinity)
    {
        this.affinity = affinity;
    }

    @Override
    public long getAffinity()
    {
        return affinity;
    }

    @Override
    public void setCapabilities(
        byte capabilities)
    {
        this.capabilities = capabilities;
    }

    @Override
    public byte getCapabilities()
    {
        return capabilities;
    }

    @Override
    protected boolean setOption0(
        String key,
        Object value)
    {
        if (super.setOption0(key, value))
        {
            return true;
        }
        else if (OPTION_TRANSMISSION.getName().equals(key))
        {
            setTransmission(ZillaTransmission.decode(Objects.toString(value, null)));
        }
        else if (OPTION_WINDOW.getName().equals(key))
        {
            setWindow(convertToInt(value));
        }
        else if (OPTION_SHARED_WINDOW.getName().equals(key))
        {
            setSharedWindow(convertToInt(value));
        }
        else if (OPTION_BUDGET_ID.getName().equals(key))
        {
            setBudgetId(convertToLong(value));
        }
        else if (OPTION_STREAM_ID.getName().equals(key))
        {
            setStreamId(convertToLong(value));
        }
        else if (OPTION_PADDING.getName().equals(key))
        {
            setPadding(convertToInt(value));
        }
        else if (OPTION_UPDATE.getName().equals(key))
        {
            setUpdate(ZillaUpdateMode.decode(Objects.toString(value, null)));
        }
        else if (OPTION_THROTTLE.getName().equals(key))
        {
            setThrottle(ZillaThrottleMode.decode(Objects.toString(value, null)));
        }
        else if (OPTION_BYTE_ORDER.getName().equals(key))
        {
            setBufferFactory(ZillaByteOrder.decode(Objects.toString(value, "native")).toBufferFactory());
        }
        else if (OPTION_AFFINITY.getName().equals(key))
        {
            setAffinity(convertToLong(value));
        }
        else if (OPTION_CAPABILITIES.getName().equals(key))
        {
            setCapabilities(convertToByte(value));
        }
        else
        {
            return false;
        }

        return true;
    }
}
