package foobar;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class MatterFull {
    public static void main(String[] args) throws Exception {
        MatterControl control = new MatterControl();
        kilim.http.HttpServer kilimServer = new kilim.http.HttpServer(9091,control.mk.sessionFactory());
        
        Server server = new Server(9090);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        ServletHolder wsHolder = new ServletHolder("echo",control.ws);
        context.addServlet(wsHolder,"/api/*");

        server.setHandler(context);
        server.start();
    }
    
}