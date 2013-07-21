import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import java.util.logging.SimpleFormatter;
import java.util.logging.FileHandler;
import org.eclipse.jetty.websocket.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.WebSocket.Connection;
final Logger log = Logger.getLogger("com.something.something");
// Strange, I think the logger now works to stdout in all threads. See what happens if you remove this.
FileHandler handler = new FileHandler("/home/sarnobat/Desktop/server.txt")
handler.setFormatter(new SimpleFormatter()); 

log.addHandler(handler);
synchronized Logger getLogger() {
	return log;
}

final Connection teacherConnection;
final Map<WebSocket.OnTextMessage, Connection> studentConnections = new HashMap<WebSocket.OnTextMessage, Connection>();
final Collection<WebSocket.OnTextMessage> studentSockets = new HashSet<WebSocket.OnTextMessage>();
try {
	final Server studentServer = new Server(8081);
	WebSocketHandler chatWebSocketHandler = new WebSocketHandler() {
		public WebSocket doWebSocketConnect(HttpServletRequest request2, String protocol2) {
			return new WebSocket.OnTextMessage() {
				Connection studentConnection;
				@Override public void onOpen(Connection conn) {
					studentConnection = conn;
					studentSockets.add(studentConnection)
					studentConnections.put(this, studentConnection);
				}

				@Override public void onClose(int closeCode, String message) {
					studentSockets.remove(studentConnection);
				}

				@Override public void onMessage(String data) {
					if (teacherConnection == null) {
						studentConnection.sendMessage('TEACHER_MISSING');
						return;
					} else {
						studentConnection.sendMessage('TEACHER_PRESENT');
					}
					try {
						teacherConnection.sendMessage(data);
						studentConnection.sendMessage('ACK::' + data);
					} catch (Exception x) {
						studentConnection.sendMessage('FAIL: ' + x.getStackTrace());
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



try {
	final Server teacherServer = new Server(8082);
	WebSocketHandler chatWebSocketHandler = new WebSocketHandler() {

		public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
			
			return new WebSocket.OnTextMessage() {
				@Override public void onOpen(Connection conn) {
					teacherConnection = conn;
					for (WebSocket.FrameConnection studentSocket : studentSockets) {
						studentSocket.sendMessage("TEACHER_JOINED");
					}
				}

				@Override public void onClose(int closeCode, String message) {
					teacherConnection = null;
					for (WebSocket.FrameConnection studentSocket : studentSockets) {
						studentSocket.sendMessage("TEACHER_LEFT");
					}
				}

				@Override public void onMessage(String data) {
					log.info(data);
					if (data.startsWith("CORRECT::")) {
						// do JDBC here
					}
					for (WebSocket.FrameConnection studentSocket : studentSockets) {
						studentSocket.sendMessage(data);
					}
				}
			};
		}
	};
	chatWebSocketHandler.setHandler(new DefaultHandler());
	teacherServer.setHandler(chatWebSocketHandler);
	teacherServer.start();
	new Runnable() {
		@Override public void run() {
			teacherServer.join();
		}
	};
} catch (Throwable e) {
	e.printStackTrace();
}