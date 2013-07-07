import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.websocket.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.WebSocket.Connection;
final Logger log = Logger.getLogger("com.something.something");
synchronized Logger getLogger() {
	return log;
}
log.info('begin');

synchronized static void doLog(String str) {
	println(str);
}
final Connection teacherConnection;
final Map<WebSocket.OnTextMessage, Connection> studentConnections = new HashMap<WebSocket.OnTextMessage, Connection>();
final Collection<WebSocket.OnTextMessage> studentSockets = new HashSet<WebSocket.OnTextMessage>();
try {
	final Server studentServer = new Server(8081);
	WebSocketHandler chatWebSocketHandler = new WebSocketHandler() {
		public WebSocket doWebSocketConnect(HttpServletRequest request2, String protocol2) {
			return new WebSocket.OnTextMessage() {
				Connection connection;
				@Override public void onOpen(Connection conn) {
					log.info("Message");
					connection = conn;
					//conn.sendMessage('success')
					studentSockets.add(connection)
					studentConnections.put(this, connection);
					println('opened student');
				}

				@Override public void onClose(int closeCode, String message) {
					studentSockets.remove(connection);
				}

				@Override public void onMessage(String data) {
					log.info("Message");
					try {
						teacherConnection.sendMessage(data + " raised a hand.");
						connection.sendMessage('RAISED');
					} catch (IOException x) {
						connection.sendMessage('FAIL');
						doLog(x);
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
			log.info('started 1');
			studentServer.join();
		}
	};
} catch (Throwable e) {
	e.printStackTrace();
}



try {
	final Server server = new Server(8082); // TODO: rename to teacherServer
	WebSocketHandler chatWebSocketHandler = new WebSocketHandler() {

		public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
			
			return new WebSocket.OnTextMessage() {
				@Override public void onOpen(Connection conn) {
					teacherConnection = conn;
				}

				@Override public void onClose(int closeCode, String message) {
					teacherConnection = null;
				}

				@Override public void onMessage(String data) {
					for (WebSocket.FrameConnection studentSocket : studentSockets) {
					//	studentConnections.get(studentSocket).sendMessage(data);
						//println(studentSocket);
						studentSocket.sendMessage(data);
					}
				}
			};
		}
	};
	chatWebSocketHandler.setHandler(new DefaultHandler());
	server.setHandler(chatWebSocketHandler);
	server.start();
	new Runnable() {
		@Override public void run() {
			log.info('started 2');
			server.join();
		}
	};
} catch (Throwable e) {
	e.printStackTrace();
}


log.info('parent thread. Why dont the child threads get printed');