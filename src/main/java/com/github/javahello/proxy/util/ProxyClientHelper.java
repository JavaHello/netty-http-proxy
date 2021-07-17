/*
 * Copyright 2021 kailuo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.javahello.proxy.util;

import com.github.javahello.proxy.conf.UpstreamServer;

import java.net.URL;

/**
 * @author kailuo
 */
public class ProxyClientHelper {
    public static UpstreamServer urlToUps(URL url) {
        UpstreamServer upstreamServer = new UpstreamServer();
        upstreamServer.setAddress(url.getHost());
        int port = 80;
        if (url.getPort() > 0) {
            port = url.getPort();
        }
        upstreamServer.setPort(port);
        return upstreamServer;
    }
}
