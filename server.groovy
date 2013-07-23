import org.json.JSONObject;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.*;

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

Class.forName("org.sqlite.JDBC");
BasicDataSource dataSource = new BasicDataSource();
dataSource.setDriverClassName("org.sqlite.JDBC");
dataSource.setUrl("jdbc:sqlite:students.db");
QueryRunner run = new QueryRunner(dataSource);

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
					String name = data.substring(7,data.length());					
					if (data.startsWith("RAISE::")) {
						try {
							int inserts = run.update( "INSERT INTO students (name,raised,correct) VALUES ('" + name + "',1,0)");
						} catch (Exception e) {
							int updates = run.update( "UPDATE students SET raised=raised+1 WHERE name='" + name + "'");
						}
					} else if  (data.startsWith("LOWER::")) {
						int updates = run.update( "UPDATE students SET raised=raised-1 WHERE name='" + name + "'");
					}
					
					Map[] result1 = run.query("SELECT name,raised,correct FROM students WHERE name = ?", new MapListHandler(), name);
					String raisedCount = result1.length > 0 ? result1[0].get("raised") : 0;
					String correctCount = result1.length > 0 ? result1[0].get("correct") : 0;
					try {
						JSONObject json = new JSONObject();
						json.put("name", data);
						json.put("raised", raisedCount);
						json.put("correct", correctCount);
						teacherConnection.sendMessage(json.toString());
						studentConnection.sendMessage('ACK::' + data);
					} catch (Exception x) {
						studentConnection.sendMessage('FAIL: ' + x.getStackTrace());
						log.info("json failure: " + x.toString());
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
					if (data.startsWith("CORRECT::")) {
						String name = data.substring(9,data.length());
						try {
							int updates = run.update( "UPDATE students SET correct=correct+1 WHERE name='" + name + "'");
						} catch (Exception e) {
							log.info(e);
						}
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
