package foobar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.function.Consumer;
import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import mm.rest.ConfigClientFormatOldReps;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;
import org.srlutils.btree.Btypes;

public class MatterLess extends HttpServlet {
    static Gson pretty = new GsonBuilder().setPrettyPrinting().create();
    static Gson gson = new Gson();
    
    MatterData dm = new MatterData();
    Db4j db4j = dm.start("./db_files/hunk.mmap",false);
    ProxyServlet proxy = new ProxyServlet();

    static String proxyPrefix = "/proxy";
    
    static String pretty(Object obj) { return pretty.toJson(obj).toString(); }
    static void print(Object obj) { System.out.println(pretty(obj)); }

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

        MatterLess mm = new MatterLess();
        ServletHolder mh = new ServletHolder(mm);
        ServletHolder ph = new ServletHolder(mm.proxy);
        ph.setInitParameter("proxyTo","http://localhost:8065");
        mh.setInitParameter("resourceBase",base);
        mh.setInitParameter("redirectWelcome","false");
        context.addServlet(mh,"/api/*");
        context.addServlet(ph,proxyPrefix+"/api/*");
        add("/*",base,context);

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
    
    public static class Routes {
        String config = "/api/v4/config/client";
    }
    Routes routes = new Routes();
    
    public static <TT extends Query> void chain(TT query,Consumer<TT> cb) {
        kilim.Task.spawn(() -> {
            query.await();
            cb.accept(query);
        });
    }
    
    Configlet configlet = new Configlet();
    public class Configlet {
        protected void response(byte[] buffer,int offset,int length) {
            String txt = new String(buffer,offset,length);
            ConfigClientFormatOldReps reply = gson.fromJson(txt,ConfigClientFormatOldReps.class);
            print(reply);
            db4j.submitCall(txn -> {
                dm.gen.insert(txn,routes.config.hashCode(),reply);
                System.out.println("inserted: " + routes.config);
            });
        }
        protected void send(Consumerx<String> found,Consumerx<Void> proxy) {
            chain(
                    db4j.submit(txn -> {
                        Object obj = dm.gen.find(txn,routes.config.hashCode());
                        System.out.println("find: " + obj);
                        return (ConfigClientFormatOldReps) obj;
                    }),
                    q -> {
                        System.out.println("matter.config.db: " + q.val);
                        if (q.val==null)
                            proxy.accept(null);
                        else
                            found.accept(gson.toJson(q.val));
                    }
            );
        }
    }
    <TT> TT set(TT val,Consumer<TT> ... consumers) {
        for (Consumer sumer : consumers)
            sumer.accept(val);
        return val;
    }
    interface Consumerx<TT> {
        void process(TT val) throws ServletException, IOException;
        default void accept(TT val) {
            try {
                process(val);
            }
            catch (Throwable ex) {
                System.out.println("Consumerx exception occurred: " + ex);
                ex.printStackTrace();
            }
        }
    }
    protected void service(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        String url = req.getRequestURI();
        System.out.println("matter: " + url);
        if (url.equals(routes.config)) {
            req.startAsync().setTimeout(0);
            configlet.send(
                    reply -> {
                        resp.getOutputStream().write(reply.getBytes());
                        req.getAsyncContext().complete();
                    },
                    dummy -> {
                        req.getAsyncContext().dispatch(proxyPrefix+url);
                    }
            );
        }
        else
            getServletContext().getRequestDispatcher(proxyPrefix+url).forward(req,resp);
    }
    public class ProxyServlet extends org.eclipse.jetty.proxy.ProxyServlet.Transparent {
        protected void onResponseContent(
                HttpServletRequest req,HttpServletResponse resp,
                Response proxy, byte[] buffer,int offset,int length,Callback callback) {
            System.out.println(req.getRequestURI());
            System.out.println(new String(buffer));
            System.out.format("----------------------------------------------------------------------------\n\n");
            if (req.getRequestURI().equals(proxyPrefix+routes.config))
                new Configlet().response(buffer,offset,length);
            super.onResponseContent(req,resp,proxy,buffer,offset,length,callback);
        }
        protected String rewriteTarget(HttpServletRequest request) {
            String url = super.rewriteTarget(request);
            return url.replaceFirst(proxyPrefix,"");
        }
    }

    
}
