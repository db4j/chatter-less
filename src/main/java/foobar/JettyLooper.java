package foobar;

import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

// proxies to a single server (kilim)
// which isn't especially useful
// but demonstrates that the process works
// and in theory could probably proxy some api calls to a different server
// unfortunately jetty doesn't support proxying ws
// which mattermost needs, so this is effectively DOA

public class JettyLooper {
    public static void main(String[] args) throws Exception {
        MatterFull.main(args);
        Server server = new Server(9092);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        ServletHolder pk = new ServletHolder(new ProxyServlet.Transparent());
        pk.setInitParameter("proxyTo","http://localhost:9091");
        context.addServlet(pk,"/*");

        server.setHandler(context);
        server.start();
    }
}
