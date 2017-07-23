package foobar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import mm.data.Users;
import mm.rest.ConfigClientFormatOldReps;
import mm.rest.LicenseClientFormatOldReps;
import mm.rest.NotifyUsers;
import mm.rest.PreferencesSaveReq;
import mm.rest.TeamsReps;
import mm.rest.UsersLogin4Error;
import mm.rest.UsersLogin4Reqs;
import mm.rest.UsersLoginReqs;
import mm.rest.UsersReps;
import mm.rest.UsersReqs;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;
import org.srlutils.Simple;

public class MatterLess extends HttpServlet {
    static Gson pretty = new GsonBuilder().setPrettyPrinting().create();
    static Gson gson;
    static {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(String.class, new PointAdapter());
        gson = builder.create();
        TeamsReps tr = new TeamsReps();
        tr.id = "hello\"world";
        String txt = gson.toJson(tr);
        System.out.println(txt);
     }
    
    MatterData dm = new MatterData();
    Db4j db4j = dm.start("./db_files/hunk.mmap",false);

    String format(Users user) {
        return user.username + ":" + user.id;
    }
    
    void printUsers()
    {
        db4j.submitCall(txn -> {
            System.out.println(
            print(dm.users.getall(txn).vals(), x->format(x)));
            System.out.println(
            print(dm.usersById.getall(txn).keys(), x->x));
        });
    }
    ProxyServlet proxy = new ProxyServlet();
    KilimProxy kproxy = new KilimProxy();
    kilim.http.HttpServer kilimServer;

    static <TT> String print(List<TT> vals,Function<TT,String> mapping) {
        return vals.stream().map(mapping).collect(Collectors.joining("\n")); }
    
    MatterLess() throws Exception {
        kilimServer = new kilim.http.HttpServer(9091,
                () -> set(new MatterKilim(),x->x.setup(this)));
    }
    
    static String proxyPrefix = "/proxy";
    static String kilimPrefix = "/kilim";
    
    static String pretty(Object obj) { return pretty.toJson(obj).toString(); }
    static void print(Object obj) { System.out.println(pretty(obj)); }

    public static class PointAdapter extends TypeAdapter<String> {
        public String read(JsonReader reader) throws IOException {
            return reader.nextString();
        }

        public void write(JsonWriter writer,String value) throws IOException {
            writer.value(value==null ? "":value);
        }
    }
    
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
    
    public static class Routes {
        String config = "/api/v4/config/client",
                users = "/api/v4/users",
                login = "/api/v3/users/login",
                login4 = "/api/v4/users/login",
                teams = "/api/v4/teams",
                um = "/api/v4/users/me",
                ump = "/api/v4/users/me/preferences",
                umt = "/api/v4/users/me/teams",
                umtm = "/api/v4/users/me/teams/members",
                umtu = "/api/v4/users/me/teams/unread",
                license = "/api/v4/license/client";
    }
    static Routes routes = new Routes();
    
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
    static <TT> TT set(TT val,Consumer<TT> ... consumers) {
        for (Consumer sumer : consumers)
            sumer.accept(val);
        return val;
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

    // string literals in gson    
    // notify props
    // https://github.com/google/gson/issues/326
    
    String salt(String plain) { return plain; }
    static MatterData.FieldCopier<UsersReqs,Users> req2users = new MatterData.FieldCopier(UsersReqs.class,Users.class);
    static MatterData.FieldCopier<Users,UsersReps> users2reps = new MatterData.FieldCopier(Users.class,UsersReps.class);
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
                int row = dm.userCount.plus(txn,1);
                dm.users.insert(txn,row,u);
                dm.usersById.insert(txn,u.id,row);
                dm.usersByName.insert(txn,u.username,row);
                System.out.println("users.insert: " + u.id + " -- " + row);
            }),
            q -> {
                replyx(resp,users2reps.copy(u));
                if (q.getEx() != null) {
                    System.out.println(q.getEx());
                    q.getEx().printStackTrace();
                }
                printUsers();
                req.getAsyncContext().complete();
            });
        }
        else if (url.equals(routes.ump)) {
            String uid = userid(req,mmuserid);
            reply(resp,new Object[] { set(new PreferencesSaveReq(),
                    x -> { x.category="tutorial_step"; x.name = x.userId = uid; x.value = "0"; }) });
        }
        else if (url.equals("/api/seth")) {
            printUsers();
            reply(resp,"running listing");
        }
        else if (url.equals(routes.um)) {
            req.startAsync().setTimeout(0);
            String userid = userid(req,mmuserid);
            chain(db4j.submit(txn -> {
                Integer row = dm.usersById.find(txn,userid);
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
                    row = dm.usersById.find(txn,login.id);
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
    static String mmuserid = "MMUSERID";
    static String mmauthtoken = "MMAUTHTOKEN";

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

    SecureRandom random = new SecureRandom();
    String newid() {
        String val = "";
        while (val.length() != 26)
            val = new BigInteger(134,random).toString(36);
        System.out.println("newid: " + val);
        return val;
    }
    
}
