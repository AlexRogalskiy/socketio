/**
 * Copyright 2012 Ronen Hamias, Anton Kharenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socketio.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socketio.netty.pipeline.SocketIOChannelInitializer;
import org.socketio.netty.session.SocketIOHeartbeatScheduler;

/**
 * A Socket.IO server launcher class.
 * 
 * @author Anton Kharenko
 */
public class SocketIOServer {

	private enum State {
		STARTED, STOPPED
	}

	private final Logger log = LoggerFactory.getLogger(getClass());

	private volatile State state = State.STOPPED;

	private ServerBootstrap bootstrap;

	private ScheduledExecutorService heartbeatScheduller;

	private ISocketIOListener listener;

	private int port = 8080;

	private int heartbeatThreadpoolSize = 5;

	private int heartbeatTimeout = 30;

	private int heartbeatInterval = 20;

	private int closeTimeout = 25;

	private String transports = "websocket,flashsocket,xhr-polling,jsonp-polling";

	private SSLContext sslContext = null;

	private boolean alwaysSecureWebSocketLocation = false;

	private String headerClientIpAddressName;
	
	private boolean epollEnabled = true;
	
	private ServerBootstrapFactory serverBootstrapFactory;
	
	/**
	 * Creates Socket.IO server with default settings.
	 */
	public SocketIOServer() {
	}

	/**
	 * Starts Socket.IO server with current configuration settings.
	 * 
	 * @throws IllegalStateException
	 *             if server already started
	 */
	public synchronized void start() {
		if (isStarted()) {
			throw new IllegalStateException("Failed to start Socket.IO server: server already started");
		}

		log.info("Socket.IO server starting");

		// Configure heartbeat scheduler
		heartbeatScheduller = Executors.newScheduledThreadPool(getHeartbeatThreadpoolSize());
		SocketIOHeartbeatScheduler.setScheduledExecutorService(heartbeatScheduller);
		SocketIOHeartbeatScheduler.setHeartbeatInterval(getHeartbeatInterval());

        
	    // Configure and bind server
	    ServerBootstrapFactory bootstrapFactory = serverBootstrapFactory != null
	        ? serverBootstrapFactory
	        : new DefaultServerBootstrapFactory(epollEnabled);
	    bootstrap = bootstrapFactory.createServerBootstrap();
	    
		// Configure server
        SocketIOChannelInitializer channelInitializer = new SocketIOChannelInitializer(
                listener,
                getHeartbeatTimeout(),
                getCloseTimeout(),
                getTransports(),
                sslContext,
                alwaysSecureWebSocketLocation,
                port,
                headerClientIpAddressName);
        
	    bootstrap.childHandler(channelInitializer);
	    bootstrap.bind(getPort()).syncUninterruptibly();
	    
		log.info("Started {}", this);

		state = State.STARTED;
	}

	/**
	 * Stops Socket.IO server.
	 * 
	 * @throws IllegalStateException
	 *             if server already stopped
	 */
	public synchronized void stop() {
		if (isStopped()) {
			throw new IllegalStateException("Failed to stop Socket.IO server: server already stopped");
		}

		log.info("Socket.IO server stopping");

		heartbeatScheduller.shutdown();
		bootstrap.group().shutdownGracefully();

		log.info("Socket.IO server stopped");

		state = State.STOPPED;
	}

	/**
	 * Restarts Socket.IO server. If server already started it stops server;
	 * otherwise it just starts server.
	 */
	public synchronized void restart() {
		if (isStarted()) {
			stop();
		}
		start();
	}

	/** 
	 * Returns if server is in started state or not.
	 */
	public boolean isStarted() {
		return state == State.STARTED;
	}

	/**
	 * Returns if server is in stopped state or not. 
	 */
	public boolean isStopped() {
		return state == State.STOPPED;
	}

	/**
	 * Socket.IO events listener.
	 */
	public ISocketIOListener getListener() {
		return listener;
	}

	/**
	 * {@link SocketIOServer#getListener}
	 */
	public void setListener(ISocketIOListener listener) {
		this.listener = listener;
	}

	/**
	 * Port on which Socket.IO server will be started. Default value is 8080.
	 */
	public int getPort() {
		return port;
	}

	/**
	 * {@link SocketIOServer#getPort}
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Sets heartbeat thread pool size. This parameter can be used for
	 * fine-tuning heartbeat scheduler performance.
	 */
	public int getHeartbeatThreadpoolSize() {
		return heartbeatThreadpoolSize;
	}

	/**
	 * {@link SocketIOServer#getListener}
	 */
	public void setHeartbeatThreadpoolSize(int heartbeatThreadpoolSize) {
		this.heartbeatThreadpoolSize = heartbeatThreadpoolSize;
	}

	/**
	 * The timeout in seconds for the client when it should send a new heart
	 * beat to the server. This value is sent to the client after a successful
	 * handshake. The default value is 30.
	 */
	public int getHeartbeatTimeout() {
		return heartbeatTimeout;
	}

	/**
	 * {@link SocketIOServer#getHeartbeatTimeout}
	 */
	public void setHeartbeatTimeout(int heartbeatTimeout) {
		this.heartbeatTimeout = heartbeatTimeout;
	}

	/**
	 * The timeout in seconds for the client, when it closes the connection it
	 * still X amounts of seconds to do re open of the connection. This value is
	 * sent to the client after a successful handshake. Default value is 25.
	 */
	public int getCloseTimeout() {
		return closeTimeout;
	}

	/**
	 * {@link SocketIOServer#getCloseTimeout}
	 */
	public void setCloseTimeout(int closeTimeout) {
		this.closeTimeout = closeTimeout;
	}

	/**
	 * A string with list of allowed transport methods separated by comma.
	 * Default value is "websocket,xhr-polling".
	 */
	public String getTransports() {
		return transports;
	}

	/**
	 * {@link SocketIOServer#getTransports}
	 */
	public void setTransports(String transports) {
		this.transports = transports;
	}

	/**
	 * The timeout in seconds for the server, we should receive a heartbeat from
	 * the client within this interval. This should be less than the heartbeat
	 * timeout. Default value is 20.
	 */
	public int getHeartbeatInterval() {
		return heartbeatInterval;
	}

	/**
	 * {@link SocketIOServer#getHeartbeatInterval}
	 */
	public void setHeartbeatInterval(int heartbeatInterval) {
		this.heartbeatInterval = heartbeatInterval;
	}

	public SSLContext getSslContext() {
		return sslContext;
	}

	public void setSslContext(SSLContext sslContext) {
		this.sslContext = sslContext;
	}

	public boolean isAlwaysSecureWebSocketLocation() {
		return alwaysSecureWebSocketLocation;
	}

	public void setAlwaysSecureWebSocketLocation(boolean alwaysSecureWebSocketLocation) {
		this.alwaysSecureWebSocketLocation = alwaysSecureWebSocketLocation;
	}
	
	public boolean isEpollEnabled() {
		return epollEnabled;
	}

	public void setEpollEnabled(boolean epollEnabled) {
		this.epollEnabled = epollEnabled;
	}

	/**
	 * @return the headerClientIpAddressName
	 */
	public String getHeaderClientIpAddressName() {
		return headerClientIpAddressName;
	}

	/**
	 * @param headerClientIpAddressName the headerClientIpAddressName to set
	 */
	public void setHeaderClientIpAddressName(String headerClientIpAddressName) {
		this.headerClientIpAddressName = headerClientIpAddressName;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SocketIOServer [port=");
		builder.append(port);
		builder.append(", heartbeatThreadpoolSize=");
		builder.append(heartbeatThreadpoolSize);
		builder.append(", heartbeatTimeout=");
		builder.append(heartbeatTimeout);
		builder.append(", heartbeatInterval=");
		builder.append(heartbeatInterval);
		builder.append(", closeTimeout=");
		builder.append(closeTimeout);
		builder.append(", transports=");
		builder.append(transports);
		builder.append(", ssl=");
		builder.append(sslContext != null);
		builder.append(", alwaysSecureWebSocketLocation=");
		builder.append(alwaysSecureWebSocketLocation);
		builder.append(", epollEnabled=");
		builder.append(epollEnabled);
		builder.append(", headerClientIpAddressName=");
		builder.append(headerClientIpAddressName);
		builder.append("]");
		return builder.toString();
	}

}
