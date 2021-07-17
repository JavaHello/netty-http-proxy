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
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyClient {
    static int maxContentLength = 1024 * 1024;
    Bootstrap clientBootstrap;
    final Map<ChannelId, HttpClient> serverMap = new ConcurrentHashMap<>();
    final Map<ChannelId, HttpClient> clientMap = new ConcurrentHashMap<>();

    private ProxyClient() {
    }


    interface Pass {
        ChannelFuture write(ChannelHandlerContext ctx, Object msg);

        Channel flush(ChannelHandlerContext ctx);
    }


    private void flush(ChannelHandlerContext ctx) {
        serverMap.get(ctx.channel().id()).flush(ctx);
    }

    private void write(ChannelHandlerContext ctx, Object msg) {
        serverMap.get(ctx.channel().id()).write(ctx, msg);
    }


    static class HttpClient implements Pass {
        public ChannelFuture channelFuture;
        UpstreamServer upstreamServer;
        private Channel ch;
        private Channel sc;

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

        public String getHost() {
            return upstreamServer.getAddress();
        }

        @Override
        public ChannelFuture write(ChannelHandlerContext ctx, Object msg) {
            return sc.write(msg);
        }

        @Override
        public Channel flush(ChannelHandlerContext ctx) {
            return sc.flush();
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
                                proxyClient.serverMap.remove(ctx.channel().id());
                                ctx.fireChannelInactive();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                proxyClient.serverMap.remove(ctx.channel().id());
                                ctx.fireExceptionCaught(cause);
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
        return clientMap.computeIfAbsent(sc.id(), k -> {
            HttpClient httpClient = new HttpClient();
            httpClient.sc = sc;
            httpClient.upstreamServer = upstreamServer;
            httpClient.channelFuture = connect(upstreamServer).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    httpClient.ch = future.channel();
                    serverMap.put(future.channel().id(), httpClient);
                } else {
                    future.cause().printStackTrace();
                }
            });
            return httpClient;
        });
    }

    public void close(ChannelHandlerContext ctx) {
        clientMap.remove(ctx.channel().id());
    }


}
