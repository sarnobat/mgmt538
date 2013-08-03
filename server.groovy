/*
	TODO:
		raise,lower, raise should only increment the raise count once
		raise,lower, raise should only show one row, not two

	SQL:		
	 alter table students add column additional_points number;
*/

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
					String statOperation;
					if (data.startsWith("RAISE::")) {
						try {
							int inserts = run.update( "INSERT INTO students (name,raised,correct) VALUES ('" + name + "',1,0)");
						} catch (Exception e) {
							int updates = run.update( "UPDATE students SET raised=raised+1 WHERE name='" + name + "'");
						}
						statOperation = "INSERT_RAISED_ROW";
					} else if (data.startsWith("LOWER::")) {
						statOperation = "UPDATE_RAISED_ROW";
						teacherConnection.sendMessage(data);
					}	
					updateStats(run, teacherConnection, data, name, log, statOperation);
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
						String[] parts = data.split("::");
						String name = parts[1];
						Integer credit = Integer.parseInt(parts[2]);
						try {
							int updates = run.update( "UPDATE students SET correct=correct+" + credit + " WHERE name='" + name + "'");
						} catch (Exception e) {
							log.info(e);
						}							
						// We received a CORRECT message from the teacher. Send the teacher
						// back the new stats for the student (from the database,
						// don't try and be clever and increment it without querying the 
						// database)
					
						updateStats(run, teacherConnection, data, name, log, "UPDATE_RAISED_ROW");
						sendToAllStudents(data, studentSockets);
					} else if (data.startsWith("CLEAR")) {
						sendToAllStudents("CLEAR", studentSockets);
					} else {
						log.info("Unknown message from teacher: " + data);
					}
					// Send "CORRECT" toto the teacher for that student only
					

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

void updateStats(QueryRunner run, Connection teacherConnection, String data, String name, Logger log, String statOperation) {
	Map[] studentRows = run.query("SELECT name,raised,correct FROM students WHERE name = ?", new MapListHandler(), name);
	String raisedCount = studentRows.length > 0 ? studentRows[0].get("raised") : 0;
	String correctCount = studentRows.length > 0 ? studentRows[0].get("correct") : 0;
	try {
		JSONObject json = new JSONObject();
		json.put("name", name);
		json.put("raised", raisedCount);
		json.put("correct", correctCount);
		json.put("operation", statOperation);
		log.info("updateStats() - about to send: " + json.toString());
		teacherConnection.sendMessage(json.toString());
	} catch (Exception x) {
		log.info("json failure: " + x.toString());
	}
	log.info("updateStats() - data:" + data + "::" +raisedCount + "::" + correctCount);
}

void sendToAllStudents(String data, Iterable<WebSocket.FrameConnection> studentSockets) {
	for (WebSocket.FrameConnection studentSocket : studentSockets) {
		studentSocket.sendMessage(data);
	}
}