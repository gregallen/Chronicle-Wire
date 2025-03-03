/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.wire.channel;

import net.openhft.chronicle.bytes.DistributedUniqueTimeProvider;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.time.SystemTimeProvider;
import net.openhft.chronicle.core.time.TimeProvider;
import net.openhft.chronicle.wire.LongConversion;
import net.openhft.chronicle.wire.NanoTimestampLongConverter;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;

/**
 * Encapsulates system-related information into a singleton object, such as:
 * the number of available processors, host ID, host name, system up time,
 * user's country and name, and the Java vendor and version.
 * <p>
 * The singleton instance is initialized with the system's current state.
 * If deserialized from another host, the SystemContext object would contain the information of that host at the moment of serialization.
 */
@SuppressWarnings("unused")
public class SystemContext extends SelfDescribingMarshallable {
    public static final SystemContext INSTANCE = getInstance();
    private int availableProcessors;
    private int hostId;
    private String hostName;
    @LongConversion(NanoTimestampLongConverter.class)
    private long upTime;
    private String userCountry;
    private String userName;
    private String javaVendor;
    private String javaVersion;

    /**
     * Singleton accessor. It lazily initializes and retrieves the SystemContext instance.
     * This method captures the system's current state including processor count, host info,
     * up time, user info and Java environment details.
     *
     * @return The singleton instance of SystemContext.
     */
    private static SystemContext getInstance() {
        SystemContext sc = new SystemContext();
        final Runtime runtime = Runtime.getRuntime();
        sc.availableProcessors = runtime.availableProcessors();
        sc.hostId = Integer.getInteger("hostId", 0);
        sc.hostName = OS.getHostName();
        final TimeProvider tp = (sc.hostId == 0 ? SystemTimeProvider.INSTANCE : DistributedUniqueTimeProvider.instance());
        sc.upTime = tp.currentTimeNanos();
        sc.userCountry = System.getProperty("user.country");
        sc.userName = OS.getUserName();
        sc.javaVendor = System.getProperty("java.vendor");
        sc.javaVersion = System.getProperty("java.version");
        return sc;
    }

    /**
     * Provides the number of available processors in the current system.
     *
     * @return Number of available processors.
     */
    public int availableProcessors() {
        return availableProcessors;
    }

    /**
     * Provides the unique identifier for the host system.
     *
     * @return System's host ID.
     */
    public int hostId() {
        return hostId;
    }

    /**
     * Provides the name of the host system.
     *
     * @return System's host name.
     */
    public String hostName() {
        return hostName;
    }

    /**
     * Provides the vendor information of the Java environment in the current system.
     *
     * @return The Java environment vendor.
     */
    public String javaVendor() {
        return javaVendor;
    }

    /**
     * Provides the version of the Java environment in the current system.
     *
     * @return The Java environment version.
     */
    public String javaVersion() {
        return javaVersion;
    }

    /**
     * Provides the system's up time in nanoseconds.
     *
     * @return The system's up time in nanoseconds.
     */
    public long upTime() {
        return upTime;
    }

    /**
     * Provides the user's country in the current system.
     *
     * @return The country code of the user.
     */
    public String userCountry() {
        return userCountry;
    }

    /**
     * Provides the username of the current user in the system.
     *
     * @return The username of the current user.
     */
    public String userName() {
        return userName;
    }
}
