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

import com.github.javahello.proxy.conf.UpstreamServer;
import com.github.javahello.proxy.util.ClassPathHelper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyClient {
    static int maxContentLength = 1024 * 1024;
    Bootstrap clientBootstrap;

    private ProxyClient() {
    }


    class ProxyPass {
        Channel sc;
        HttpClient httpClient;
        Pass pass;

        public void flush(ChannelHandlerContext ctx) {
            pass.flush(ctx);
        }

        public void write(ChannelHandlerContext ctx, Object msg) {
            pass.write(ctx, msg);
        }

        public void close(ChannelHandlerContext ctx) {
            pass.close(ctx);
        }
    }

    interface Pass {
        ChannelFuture write(ChannelHandlerContext ctx, Object msg);

        Channel flush(ChannelHandlerContext ctx);

        void close(ChannelHandlerContext ctx);
    }

    Map<ChannelId, ProxyPass> serverChMap = new ConcurrentHashMap<>();

    public void registerPass(Channel sc, HttpClient httpClient) {
        ProxyPass proxyPass = new ProxyPass();
        proxyPass.sc = sc;
        proxyPass.httpClient = httpClient;
        Optional.ofNullable(httpClient.ch).ifPresentOrElse(ch -> {
            createPass(sc, httpClient, proxyPass);
            serverChMap.put(ch.id(), proxyPass);
        }, () -> {
            httpClient.channelFuture.addListener((ChannelFutureListener) e -> {
                if (e.isSuccess()) {
                    createPass(sc, httpClient, proxyPass);
                    serverChMap.put(e.channel().id(), proxyPass);
                }
            });
        });
    }

    private void createPass(Channel sc, HttpClient httpClient, ProxyPass proxyPass) {
        proxyPass.pass = new Pass() {
            @Override
            public ChannelFuture write(ChannelHandlerContext ctx, Object msg) {
                return sc.write(msg);
            }

            @Override
            public Channel flush(ChannelHandlerContext ctx) {
                return sc.flush();
            }

            @Override
            public void close(ChannelHandlerContext ctx) {
                serverChMap.remove(ctx.channel().id());
                httpClient.close();
            }
        };
    }

    private void flush(ChannelHandlerContext ctx) {
        serverChMap.get(ctx.channel().id()).flush(ctx);
    }

    private void write(ChannelHandlerContext ctx, Object msg) {
        serverChMap.get(ctx.channel().id()).write(ctx, msg);
    }

    private void close(ChannelHandlerContext ctx) {
        serverChMap.get(ctx.channel().id()).close(ctx);
    }


    class HttpClient {
        public ChannelFuture channelFuture;
        UpstreamServer upstreamServer;
        private Channel ch;

        private HttpClient() {
        }

        public ChannelFuture writeAndFlush(Object msg) {
            if (ch != null) {
                return ch.writeAndFlush(msg);
            } else {
                return channelFuture.addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        future.channel().writeAndFlush(msg);
                    } else {
                        future.cause().printStackTrace();
                    }
                });
            }
        }

        public String getPrefix() {
            return upstreamServer.getPrefix();
        }

        public void close() {
            Optional.ofNullable(ch).map(Channel::close);
        }

        public String getHost() {
            return upstreamServer.getAddress();
        }
    }

    public static ProxyClient create(Bootstrap clientBootstrap, EventLoopGroup eventLoopGroup) {
        ProxyClient proxyClient = new ProxyClient();
        proxyClient.clientBootstrap = clientBootstrap;
        clientBootstrap
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline cp = ch.pipeline();
                        //包含编码器和解码器
                        cp.addLast(new HttpClientCodec());
                        //聚合
                        cp.addLast(new HttpObjectAggregator(maxContentLength));
                        //解压
                        cp.addLast(new HttpContentDecompressor());
                        cp.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                proxyClient.write(ctx, msg);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                proxyClient.flush(ctx);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                proxyClient.close(ctx);
                            }
                        });
                    }
                });
        return proxyClient;
    }

    public ChannelFuture connect(UpstreamServer upstreamServer) {
        return clientBootstrap.connect(new InetSocketAddress(upstreamServer.getAddress(), upstreamServer.getPort()));
    }

    public HttpClient createHttpClient(Channel sc, UpstreamServer upstreamServer) {
        HttpClient httpClient = new HttpClient();
        httpClient.upstreamServer = upstreamServer;
        httpClient.channelFuture = connect(upstreamServer).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                httpClient.ch = future.channel();
            } else {
                future.cause().printStackTrace();
            }
        });
        registerPass(sc, httpClient);
        return httpClient;
    }


}
