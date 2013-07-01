import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.WebSocket.Connection;

final Connection teacherConnection;
try {
	final Server server = new Server(8082);
	WebSocketHandler chatWebSocketHandler = new WebSocketHandler() {

		public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
			try {
				final Server studentServer = new Server(8081);
				WebSocketHandler chatWebSocketHandler = new WebSocketHandler() {
					Connection connection;
					public WebSocket doWebSocketConnect(HttpServletRequest request2, String protocol2) {
						return new WebSocket.OnTextMessage() {
							@Override public void onOpen(Connection conn) {
								connection = conn;
							}
			
							@Override public void onClose(int closeCode, String message) {
								connection = null;
							}
			
							@Override public void onMessage(String data) {
								try {
									teacherConnection.sendMessage(data + " joined.");
								} catch (IOException x) {
									connection.close();
								}
							}
						};
					}
				};
				chatWebSocketHandler.setHandler(new DefaultHandler());
				studentServer.setHandler(chatWebSocketHandler);
				studentServer.start();
				new Runnable() {
					@Override public void run() {
						studentServer.join();
					}
				};
			} catch (Throwable e) {
				e.printStackTrace();
			}


			return new WebSocket.OnTextMessage() {
				@Override public void onOpen(Connection conn) {
					teacherConnection = conn;
				}

				@Override public void onClose(int closeCode, String message) {
					teacherConnection = null;
				}

				@Override public void onMessage(String data) {
				}
			};
		}
	};
	chatWebSocketHandler.setHandler(new DefaultHandler());
	server.setHandler(chatWebSocketHandler);
	server.start();
	new Runnable() {
		@Override public void run() {
			server.join();
		}
	};
} catch (Throwable e) {
	e.printStackTrace();
}


