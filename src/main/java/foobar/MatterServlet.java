package foobar;

import static foobar.MatterRoutes.routes;
import static foobar.MatterControl.*;
import static foobar.Utilmm.*;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import mm.data.Users;
import mm.rest.ConfigClientFormatOldReps;
import mm.rest.LicenseClientFormatOldReps;
import mm.rest.PreferencesSaveReq;
import mm.rest.UsersLogin4Error;
import mm.rest.UsersLogin4Reqs;
import mm.rest.UsersLoginReqs;
import mm.rest.UsersReqs;
import org.db4j.Db4j;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;
import org.srlutils.Simple;

// the proxy to kilim never worked right and errors seemed to trigger 100% cpu usage
// it's preserved here as example code only

public class MatterServlet extends HttpServlet {
    MatterControl matter;
    MatterData dm;
    Db4j db4j;

    ProxyServlet proxy = new ProxyServlet();
    KilimProxy kproxy = new KilimProxy();

    
    static String proxyPrefix = "/proxy";
    static String kilimPrefix = "/kilim";
    
    public MatterServlet initLess(MatterControl matter) {
        this.matter = matter;
        dm = matter.dm;
        db4j = matter.db4j;
        return this;
    }
    
    public static void main(String[] args) throws Exception {
        Server server = new Server(9090);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        ServletHolder holder = new ServletHolder(new DiaryServlet());
        context.addServlet(holder,"/diary/*");

        String base = "./mattermost/webapp/dist/";

        add("/static/*",base,context);
        context.setWelcomeFiles(new String[] {"root.html"});
//        add("/*",base,context);

        MatterServlet mm = new MatterServlet();
        ServletHolder mh = new ServletHolder(mm);
        ServletHolder ph = new ServletHolder(mm.proxy);
        ServletHolder pk = new ServletHolder(mm.kproxy);
        ph.setInitParameter("proxyTo","http://localhost:8065");
        pk.setInitParameter("proxyTo","http://localhost:9091");
        mh.setInitParameter("resourceBase",base);
        mh.setInitParameter("redirectWelcome","false");
        context.addServlet(mh,"/api/*");
        context.addServlet(ph,proxyPrefix+"/api/*");
        context.addServlet(pk,kilimPrefix+"/*");
        add("/*",base,context);

        server.setHandler(context);
        server.start();
        while (true) {
            Simple.sleep(2000);
            Simple.nop();
        }
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
        protected void send(Consumerx<Object> found,Consumerx<Void> proxy) {
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
                            found.accept(q.val);
                    }
            );
        }
    }
    void xconsumerx(Consumerx<Void> cx) {
        cx.accept(null);
    }
    <TT> void consumerx(Consumerx<TT> cx,TT val) {
        cx.accept(val);
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

    String userid(HttpServletRequest req,String name) {
        Cookie [] cookies = req.getCookies();
        for (Cookie cc : cookies)
            if (cc.getName().equals(name))
                return cc.getValue();
        return "";
    }

    protected void service(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        String url = req.getRequestURI();
        System.out.println("matter: " + url);
        if (url.equals(routes.config)) {
            req.startAsync().setTimeout(0);
            configlet.send(
                    reply -> {
                        reply(resp,reply);
                        req.getAsyncContext().complete();
                    },
                    dummy -> {
                        req.getAsyncContext().dispatch(proxyPrefix+url);
                    }
            );
        }
        else if (url.equals(routes.users)) {
            req.startAsync().setTimeout(0);
            UsersReqs ureq = gson.fromJson(req.getReader(),UsersReqs.class);
            Users u = req2users.copy(ureq,new Users());
            u.id = newid();
            u.password = salt(ureq.password);
            u.updateAt = u.lastPasswordUpdate = u.createAt = new java.util.Date().getTime();
            u.roles = "system_user";
            u.notifyProps = null; // new NotifyUsers().init(rep.username);
            u.locale = "en";
//            u.authData=u.authService=u.firstName=u.lastName=u.nickname=u.position="";
            chain(db4j.submitCall(txn -> {
                int row = dm.idcount.plus(txn,1);
                dm.users.insert(txn,row,u);
                dm.idmap.insert(txn,u.id,row);
                dm.usersByName.insert(txn,u.username,row);
                System.out.println("users.insert: " + u.id + " -- " + row);
            }),
            q -> {
                replyx(resp,users2reps.copy(u));
                if (q.getEx() != null) {
                    System.out.println(q.getEx());
                    q.getEx().printStackTrace();
                }
                matter.printUsers();
                req.getAsyncContext().complete();
            });
        }
        else if (url.equals(routes.umPreferences)) {
            String uid = userid(req,mmuserid);
            reply(resp,new Object[] { set(new PreferencesSaveReq(),
                    x -> { x.category="tutorial_step"; x.name = x.userId = uid; x.value = "0"; }) });
        }
        else if (url.equals("/api/seth")) {
            matter.printUsers();
            reply(resp,"running listing");
        }
        else if (url.equals(routes.um)) {
            req.startAsync().setTimeout(0);
            String userid = userid(req,mmuserid);
            chain(db4j.submit(txn -> {
                Integer row = dm.idmap.find(txn,userid);
                return row==null ? null : dm.users.find(txn,row);
            }),
            q -> {
                if (q.val==null)
                    replyError(resp, HttpServletResponse.SC_BAD_REQUEST, "user not found");
                else {
                    // fixme::fakeSecurity - add auth token (and check for it on requests)
                    replyx(resp, users2reps.copy(q.val));
                }
                req.getAsyncContext().complete();
            });
            
        }
        else if (url.equals(routes.login) | url.equals(routes.login4)) {
            req.startAsync().setTimeout(0);
            boolean v4 = url.equals(routes.login4);
            UsersLoginReqs login = v4 ? null : gson.fromJson(req.getReader(),UsersLoginReqs.class);
            UsersLogin4Reqs login4 = !v4 ? null : gson.fromJson(req.getReader(),UsersLogin4Reqs.class);
            String password = v4 ? login4.password : login.password;
            chain(db4j.submit(txn -> {
                Integer row;
                if (login4==null)
                    row = dm.idmap.find(txn,login.id);
                else row = dm.usersByName.find(txn,login4.loginId);
                if (row==null) {
                    print(login);
                    print(login4);
                }
                return row==null ? null : dm.users.find(txn,row);
            }),
            q -> {
                if (q.val==null || ! q.val.password.equals(password)) {
                    String msg = q.val==null ? "user not found" : "invalid password";
                    replyError(resp, HttpServletResponse.SC_BAD_REQUEST, msg);

                    if (false)
                    xconsumerx($ -> reply(
                            resp,
                            set(new UsersLogin4Error(), x -> { x.message="stuff"; x.statusCode=400; })
                    ));
                }
                else {
                    // fixme::fakeSecurity - add auth token (and check for it on requests)
                    cookie(resp,mmuserid,q.val.id);
                    cookie(resp,mmauthtoken,q.val.id);
                    replyx(resp, users2reps.copy(q.val));
                }
                req.getAsyncContext().complete();
            });
            
        }
        else if (url.equals(routes.umt))
            reply(resp,new int[0]);
        else if (url.equals(routes.umtm))
            reply(resp,new int[0]);
        else if (url.equals(routes.umtu))
            reply(resp,new int[0]);
        else if (url.equals(routes.license))
            reply(resp,new LicenseClientFormatOldReps());
        else if (url.equals("/api/v3/users/websocket"))
            reply(resp,"not available");
        else if (false)
            getServletContext().getRequestDispatcher(proxyPrefix+url).forward(req,resp);
        else if (url.equals("/api/v4/teams") | true)
            getServletContext().getRequestDispatcher(kilimPrefix+url).forward(req,resp);
        else {
            reply(resp,"");
            System.out.println("unhandled: " + url);
        }
    }

    void cookie(HttpServletResponse resp,String name,String val) {
        Cookie cookie = new Cookie(name,val);
        cookie.setPath("/");
        cookie.setMaxAge(2592000);
        resp.addCookie(cookie);
    }
    
    <TT> void replyError(HttpServletResponse resp,int code,String msg,Consumerx<TT> ... cx) {
        resp.setStatus(code);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        replyx(resp, new UsersLogin4Error(), x -> { x.message=msg; x.statusCode=code; });

        // example of the more explicit way of doing this
        if (false)
            xconsumerx($ -> reply(
                    resp,
                    set(new UsersLogin4Error(), x -> { x.message="stuff"; x.statusCode=400; })
            ));
    }
    
    /** apply the consumers to val and then write it to the resp output stream as json, wrapping any errors */
    <TT> void replyx(HttpServletResponse resp,TT val,Consumerx<TT> ... cx) {
        for (Consumerx sumer : cx)
            sumer.accept(val);
        consumerx($ -> reply(resp,val),null);
    }
    
    void reply(HttpServletResponse resp,Object obj) throws IOException {
        resp.getOutputStream().write(gson.toJson(obj).getBytes());
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
    public static class KilimProxy extends org.eclipse.jetty.proxy.ProxyServlet.Transparent {
        protected void onResponseContent(
                HttpServletRequest req,HttpServletResponse resp,
                Response proxy, byte[] buffer,int offset,int length,Callback callback) {
            System.out.println("kilim.proxy: "+req.getRequestURI());
            System.out.println(new String(buffer));
            System.out.format("----------------------------------------------------------------------------\n\n");
            super.onResponseContent(req,resp,proxy,buffer,offset,length,callback);
        }
        protected String rewriteTarget(HttpServletRequest request) {
            String url = super.rewriteTarget(request);
            return url.replaceFirst(kilimPrefix,"");
        }
    }

}
