import java.nio.file.*;
import java.sql.*;
import java.util.*;

class InspectLocalSchema {
    public static void main(String[] args) throws Exception {
        Map<String,String> env=new HashMap<>();
        for(String line:Files.readAllLines(Path.of(".env"))) {
            String text=line.trim();if(text.isEmpty() || text.startsWith("#"))continue;
            int split=text.indexOf('=');if(split<1)continue;
            String value=text.substring(split+1).trim();
            if(value.length()>1 && ((value.startsWith("\"")&&value.endsWith("\"")) || (value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);
            env.put(text.substring(0,split).trim(),value);
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        // This helper can only inspect the explicitly local development database.
        try(Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/yume?serverTimezone=Asia/Seoul",env.get("DB_HOST_USERNAME"),env.get("DB_HOST_PASSWORD"))) {
            c.setReadOnly(true);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank")) {
                while(r.next())System.out.println(r.getString(1)+" | "+r.getString(2)+" | "+r.getBoolean(3));
            }
        }
    }
}
