package foobar;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;


public class MatterLess extends HttpServlet {

    public static void main(String[] args) throws Exception {
        Server server = new Server(9090);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        ServletHolder holder = new ServletHolder(new DiaryServlet());
        context.addServlet(holder,"/diary/*");


        String base = "/home/lytles/working/fun/chernika/mattermost/webapp/dist/";

//        add("/static/*",base,context);
//        context.setContextPath(base);
        context.setWelcomeFiles(new String[]{ "root.html" });
        add("/*",base,context);

    
        server.setHandler(context);
        server.start();
    }
    
    static void add(String prefix,String dre,ServletContextHandler context) {
        ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
        holderHome.setInitParameter("resourceBase",dre);
        holderHome.setInitParameter("dirAllowed","false");
        holderHome.setInitParameter("pathInfoOnly","true");
        holderHome.setInitParameter("redirectWelcome","false");
        context.addServlet(holderHome,prefix);
    }
    
    public static class DiaryServlet extends HttpServlet {
        protected void service(final HttpServletRequest req,final HttpServletResponse resp) throws ServletException, IOException {
            String txt = "hello";
            resp.getOutputStream().write(txt.getBytes());
        }
    }
}
