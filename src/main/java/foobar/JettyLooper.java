/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package foobar;

import static foobar.MatterLess.add;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;
import org.srlutils.Simple;

/**
 *
 * @author lytles
 */
public class JettyLooper extends HttpServlet {
    static String proxyPrefix = "/proxy";
    public static void main(String[] args) throws Exception {
        Server server = new Server(9090);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        String base = "/home/lytles/working/fun/chernika/mattermost/webapp/dist/";

        add("/static/*",base,context);
        context.setWelcomeFiles(new String[] {"root.html"});
//        add("/*",base,context);

        MatterLess mm = new MatterLess();
        ServletHolder wsHolder = new ServletHolder("echo",mm.ws);
        context.addServlet(wsHolder,"/api/*");

        KilimProxy kproxy = new KilimProxy();
        JettyLooper looper = new JettyLooper();
        ServletHolder mh = new ServletHolder(looper);
        ServletHolder pk = new ServletHolder(kproxy);
        pk.setInitParameter("proxyTo","http://localhost:9091");
        mh.setInitParameter("resourceBase",base);
        mh.setInitParameter("redirectWelcome","false");
//        context.addServlet(mh,"/api/*");
        context.addServlet(pk,proxyPrefix+"/*");
        add("/*",base,context);

        server.setHandler(context);
        server.start();
        
        
    }
    protected void service(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        String url = req.getRequestURI();
        System.out.println("matter: " + url);
        getServletContext().getRequestDispatcher(proxyPrefix+url).forward(req,resp);
    }
    public static class KilimProxy extends org.eclipse.jetty.proxy.ProxyServlet.Transparent {
        protected void onResponseContent(
                HttpServletRequest req,HttpServletResponse resp,
                Response proxy, byte[] buffer,int offset,int length,Callback callback) {
            System.out.println("proxy: "+req.getRequestURI());
            System.out.println(new String(buffer));
            System.out.format("----------------------------------------------------------------------------\n\n");
            super.onResponseContent(req,resp,proxy,buffer,offset,length,callback);
        }
        protected String rewriteTarget(HttpServletRequest request) {
            String url = super.rewriteTarget(request);
            return url.replaceFirst(proxyPrefix,"");
        }
    }
    
}
