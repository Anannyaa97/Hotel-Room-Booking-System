package db;

import java.sql.Connection;
import java.sql.DriverManager;

public class Database {
	// Update these constants if your DB runs with different credentials/host
	private static final String HOST = "localhost";
	private static final int PORT = 3306;
	private static final String DB_NAME = "hotel_db";
	private static final String USER = "root";
	private static final String PASS = "";

	// Returns a Connection to jdbc:mysql://HOST:PORT/DB_NAME
	public static Connection connect() throws Exception {
		Class.forName("com.mysql.cj.jdbc.Driver");
		String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", HOST, PORT, DB_NAME);
		return DriverManager.getConnection(url, USER, PASS);
	}
}