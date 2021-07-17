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
package com.github.javahello.proxy.conf;


import java.util.List;
import java.util.Map;

public class ProxyConf {

    Map<String, Upstream> upstream;
    List<Server> servers;

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public Map<String, Upstream> getUpstream() {
        return upstream;
    }

    public void setUpstream(Map<String, Upstream> upstream) {
        this.upstream = upstream;
    }

    public static class Upstream {
        List<String> server;

        public List<String> getServer() {
            return server;
        }

        public void setServer(List<String> server) {
            this.server = server;
        }
    }

    public static class Server {
        int listen;
        String serverName;
        Map<String, Location> location;

        public int getListen() {
            return listen;
        }

        public void setListen(int listen) {
            this.listen = listen;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public Map<String, Location> getLocation() {
            return location;
        }

        public void setLocation(Map<String, Location> location) {
            this.location = location;
        }
    }

    public static class Location {
        String proxyPass;


        public String getProxyPass() {
            return proxyPass;
        }

        public void setProxyPass(String proxyPass) {
            this.proxyPass = proxyPass;
        }
    }
}
