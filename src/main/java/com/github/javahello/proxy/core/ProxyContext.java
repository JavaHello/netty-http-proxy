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

import com.github.javahello.proxy.conf.ProxyConf;
import com.github.javahello.proxy.conf.UpstreamServer;
import com.github.javahello.proxy.util.ProxyClientHelper;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author kailuo
 */
public class ProxyContext {
    private ProxyConf proxyConf;
    Map<String, List<UpstreamServer>> upsRouterMap = new HashMap<>();
    Map<String, AtomicInteger> nextServer = new ConcurrentHashMap<>();

    private ProxyContext() {
    }

    public static ProxyContext create(ProxyConf proxyConf) throws MalformedURLException {
        ProxyContext proxyContext = new ProxyContext();
        proxyContext.proxyConf = proxyConf;
        for (ProxyConf.Server server : proxyConf.getServers()) {
            Map<String, ProxyConf.Location> location = server.getLocation();
            for (Map.Entry<String, ProxyConf.Location> locationEntry : location.entrySet()) {
                ProxyConf.Location l = locationEntry.getValue();
                URL url = new URL(l.getProxyPass());
                proxyContext.findUpstreamServer(url.getHost()).orElseGet(() -> {
                    UpstreamServer upstreamServer = ProxyClientHelper.urlToUps(url);
                    String usk = upstreamServer.toKey();
                    proxyContext.nextServer.put(usk, new AtomicInteger(0));
                    return proxyContext.upsRouterMap.computeIfAbsent(usk, k -> Arrays.asList(upstreamServer));
                });
            }
        }
        return proxyContext;
    }


    /**
     * 使用域名查询后端服务
     *
     * @param hostname
     * @return
     */
    public Optional<List<String>> findUpstream(String hostname) {
        ProxyConf.Upstream upstream = proxyConf.getUpstream().get(hostname);
        return Optional.ofNullable(upstream).map(ProxyConf.Upstream::getServer);
    }

    public boolean hasUpstream(String hostname) {
        return proxyConf.getUpstream().containsKey(hostname);
    }

    /**
     * 使用域名查询后端服务
     *
     * @param hostname
     * @return
     */
    public Optional<List<UpstreamServer>> findUpstreamServer(String hostname) {
        return findUpstream(hostname).map(e -> upsRouterMap.computeIfAbsent(hostname, k -> {
            nextServer.put(hostname, new AtomicInteger(0));
            List<UpstreamServer> upstreamServers = new ArrayList<>();
            for (String s : e) {
                upstreamServers.add(createUps(s));
            }
            return upstreamServers;
        }));
    }

    public Optional<UpstreamServer> findClientUps(String hostname) {
        AtomicInteger atomicInteger = nextServer.get(hostname);
        return Optional.ofNullable(atomicInteger).map(a -> {
            int i = atomicInteger.incrementAndGet();
            List<UpstreamServer> upstreamServers = upsRouterMap.get(hostname);
            return upstreamServers.get(i % upstreamServers.size());
        });
    }

    private static UpstreamServer createUps(String s) {
        String[] ss = s.split(":");
        int port = 80;
        if (ss.length == 2) {
            port = Integer.parseInt(ss[1]);
        }
        UpstreamServer upstreamServer = new UpstreamServer();
        upstreamServer.setAddress(ss[0]);
        upstreamServer.setPort(port);
        return upstreamServer;
    }
}
