import org.json.*;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.apache.commons.io.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

final Logger log = Logger.getLogger("com.something.something");
 
class MyHandler implements HttpHandler {
	final Logger log = Logger.getLogger("com.something.something");
	MyHandler() {
		log.info("MyHandler() - begin");
		println('constructo');
	}

	public Map<String, String> getQueryMap(String query)  
	{
		log.info("getQueryMap() - begin");
		Pattern pattern = Pattern.compile("/\\?.*");

		Matcher matcher = pattern.matcher(query);
		String[] params = query.split("&");  
		Map<String, String> map = new HashMap<String, String>();  
		for (String param : params)  
		{  
			String name = param.split("=")[0];  
			String value = param.split("=")[1];  
			map.put(name, value);  
		}  
		return map;  
	}  

	public void handle(HttpExchange t) throws IOException {
		println("handle() - begin");
		Class.forName("org.sqlite.JDBC");
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:students.db");
        QueryRunner run = new QueryRunner(dataSource);

		log.info("handle() - 2");

		Map[] studentRows = run.query("SELECT name,raised,correct FROM students", new MapListHandler());
		log.info("handle() - 2.5");
        String raisedCount = studentRows.length > 0 ? studentRows[0].get("raised") : 0;
		log.info("handle() - 2.6");

        JSONArray json = new JSONArray();
		log.info("handle() - 3");

        for (Map row : studentRows ){
			System.out.println(row);
			JSONObject obj = new JSONObject();
			obj.put("name",row.get("name"));
			log.info("handle() - 4");
			json.put(obj);
			log.info("handle() - " + row);
        }
		log.info("handle() - 5");
		
		t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
		t.getResponseHeaders().add("Content-type", "application/json");

		OutputStream os = t.getResponseBody();
		try {
		JSONObject ret = new JSONObject();
		ret.put(  "aaData",json);
		t.sendResponseHeaders(200, ret.toString().length());
		os.write(ret.toString().getBytes());
		os.close();
		log.info("handle() - 9");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
    
HttpServer server = HttpServer.create(new InetSocketAddress(4444), 0);
server.createContext("/", new MyHandler());
server.setExecutor(null); // creates a default executor
log.info("About to start server...");
server.start();

