package foobar;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;

public class MatterLess extends HttpServlet {

    public static void main(String[] args) throws Exception {
        Server server = new Server(9090);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        ServletHolder holder = new ServletHolder(new DiaryServlet());
        context.addServlet(holder,"/diary/*");

        String base = "/home/lytles/working/fun/chernika/mattermost/webapp/dist/";

        add("/static/*",base,context);
        context.setWelcomeFiles(new String[] {"root.html"});
//        add("/*",base,context);

        ServletHolder proxyServlet = new ServletHolder(ProxyServlet.class);
        proxyServlet.setInitParameter("proxyTo","http://localhost:8065");
        proxyServlet.setInitParameter("resourceBase",base);
        proxyServlet.setInitParameter("redirectWelcome","false");
        context.addServlet(proxyServlet,"/*");

        server.setHandler(context);
        server.start();
    }

    static void add(String prefix,String dre,ServletContextHandler context) {
        ServletHolder holderHome = new ServletHolder("static-home",DefaultServlet.class);
        holderHome.setInitParameter("resourceBase",dre);
        holderHome.setInitParameter("dirAllowed","false");
        holderHome.setInitParameter("pathInfoOnly","true");
        holderHome.setInitParameter("redirectWelcome","false");
        context.addServlet(holderHome,prefix);
    }

    public static class DiaryServlet extends HttpServlet {
        protected void service(final HttpServletRequest req,final HttpServletResponse resp) throws ServletException,IOException {
            String txt = "hello";
            resp.getOutputStream().write(txt.getBytes());
        }
    }

    // http://reanimatter.com/2016/01/25/embedded-jetty-as-http-proxy/
    // http://www.eclipse.org/jetty/documentation/9.4.x/http-client-api.html
    // https://stackoverflow.com/questions/32785018/configuring-jetty-websocket-client-to-use-proxy
    // https://github.com/dekellum/jetty/blob/master/example-jetty-embedded/src/main/java/org/eclipse/jetty/embedded/ProxyServer.java
    
    public static class ProxyServlet extends org.eclipse.jetty.proxy.ProxyServlet.Transparent {
        @Override
        protected void onResponseContent(HttpServletRequest request,HttpServletResponse response,Response proxyResponse,byte[] buffer,int offset,int length,Callback callback) {
            System.out.println(request.getRequestURI());
            System.out.println(new String(buffer));
            System.out.format("----------------------------------------------------------------------------\n\n");
            super.onResponseContent(request,response,proxyResponse,buffer,offset,length,callback);
        }

        protected String rewriteTarget(HttpServletRequest request) {
            String rewrittenURI = super.rewriteTarget(request);
            return rewrittenURI;
        }

    }

}
