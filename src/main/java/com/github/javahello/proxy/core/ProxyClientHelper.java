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
package com.github.javahello.proxy.core;

import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kailuo
 */
public class ProxyClientHelper {
    public static List<ProxyClient> createClients(List<String> server, Channel sc, String path) {
        List<ProxyClient> proxyClients = new ArrayList<>();
        for (String s : server) {
            String[] ss = s.split(":");
            int port = 80;
            if (ss.length == 2) {
                port = Integer.parseInt(ss[1]);
            }
            proxyClients.add(ProxyClient.create(sc, ss[0], port, path));
        }
        return proxyClients;
    }
}
