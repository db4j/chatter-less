package foobar;

import com.google.gson.JsonElement;
import static foobar.MatterControl.gson;
import static foobar.Utilmm.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import kilim.Pausable;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.nio.NioSelectorScheduler.SessionFactory;
import mm.data.Sessions;
import mm.rest.UsersLogin4Error;
import org.db4j.Bmeta;
import org.db4j.Db4j;

public class MatterKilim extends KilimMvc {
    MatterControl matter;
    boolean yoda = true;

    
    void setup(MatterControl $matter) {
        matter = $matter;
        if (route.isEmpty()) {
            MatterRoutes proc = scan(x -> new MatterRoutes(x).setup(matter),pp -> pp.auth());
            fallback = proc.fallback;
        }
        else
            throw new RuntimeException("MatterKilim.setup should only be called once per instance");
    }

    public SessionFactory sessionFactory() {
        return () -> new Session(this::handle);
    }
    

    public static class AuthRouter<PP extends AuthRouter> extends Router<PP> {
        MatterControl matter;
        Db4j db4j;
        MatterData dm;
        MatterWebsocket ws;
        String uid;
        Sessions mmauth;
        Integer kauth;
        String etag;

        AuthRouter(Consumer<Route> mk) { super(mk); }

        PP setup(MatterControl $matter) {
            matter = $matter;
            db4j = matter.db4j;
            dm = matter.dm;
            ws = matter.ws;
            return (PP) this;
        }

        <KK,VV> VV get(Bmeta<?,KK,VV,?> map,KK key) throws Pausable {
            return db4j.submit(txn -> map.find(txn,key)).await().val;
        }
        public <TT> TT select(Db4j.Utils.QueryFunction<TT> body) throws Pausable {
            return db4j.submit(body).await().val;
        }
        public void call(Db4j.Utils.QueryCallable body) throws Pausable {
            db4j.submitCall(body).await();
        }

        long etag() {
            try { return Long.parseUnsignedLong(etag,36); }
            catch (Exception ex) { return 0; }
        }    
        
        // fixme - could defer this parsing till the route has been determined and only parse if required
        void auth() throws Pausable {
            String cookie = req.getHeader("Cookie");
            String auth = req.getHeader("authorization");
            String token = either(
                    getCookies(cookie,MatterControl.mmauthtoken+"=")[0],
                    auth==null ? null:parse(auth,"BEARER "));
            etag = req.getHeader("If-None-Match");

            if (token != null)
                call(txn -> {
                    Integer ksess = dm.sessionMap.find(txn,token);
                    if (ksess != null)
                        mmauth = dm.sessions.find(txn,ksess);
                    if (mmauth != null) {
                        kauth = ksess;
                        uid = mmauth.userId;
                    }
                    else
                        System.out.format("token not found: %d, %s, %s\n",ksess,cookie,auth);
                });
            if (kauth==null) {
                System.out.println("cookie: "+cookie);
                System.out.println("auth: "+auth);
                logout();
            }
        }
        public Object logout() throws Pausable {
            String max = "Max-Age=0";
            if (kauth != null)
                call(txn -> dm.sessions.remove(txn,kauth));
            setCookie(resp,MatterControl.mmuserid,"",max,false);
            setCookie(resp,MatterControl.mmauthtoken,"",max,true);
            return "";
        }        

        public <TT> TT body(Class<TT> klass) {
            String txt = body();
            TT val = gson.fromJson(txt,klass);
            boolean dbg = true;
            if (dbg) {
                JsonElement parsed = MatterControl.parser.parse(txt);
                String v1 = MatterControl.skipGson.toJson(val);
                String v2 = MatterControl.skipGson.toJson(parsed);
                if (! v1.equals(v2)) {
                    System.out.format("%-40s --> %s\n",req.uriPath,klass.getName());
                    System.out.println("\t" + v1);
                    System.out.println("\t" + v2);
                    System.out.println("\t" + txt);
                }
            }
            return val;
        }
        String body() {
            return req.extractRange(req.contentOffset,req.contentOffset+req.contentLength);
        }
        byte [] rawBody() {
            return req.extractBytes(req.contentOffset,req.contentOffset+req.contentLength);
        }
    }
    public void write(HttpResponse resp,Object obj,boolean dbg) throws IOException {
        if (obj==null) return;
        byte[] msg = null;
        if (obj instanceof String) msg = ((String) obj).getBytes();
        else if (obj instanceof byte[]) msg = (byte[]) obj;
        else msg = gson.toJson(obj).getBytes();
        if (dbg)
            System.out.println("kilim.write: " + msg);
        sendJson(resp,msg);
    }
    static File urlToPath(HttpRequest req) {
        String base = "./mattermost/webapp/dist";
        String uri = req.uriPath;
        String path = (uri!=null && uri.startsWith("/static/")) ? uri.replace("/static",""):"/root.html";
        return new File(base+path);
    }
    public void handle(Session session,HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        Object reply = null;
        boolean isnull = req.uriPath==null;
        boolean isstatic = !isnull && req.uriPath.startsWith("/static/");
        boolean isapi = !isnull && req.uriPath.startsWith("/api/");



        if (!isapi) {
            if (isstatic)
                cacheControl(resp,36921603);
            else {
                AuthRouter pp = new AuthRouter(null).setup(matter);
                pp.init(session,req,resp);
                pp.auth();
            }
            File file = urlToPath(req);
            session.sendFile(req,resp,file);
        }
        else if (yoda)
        try {
            reply = route(session,req,resp);
        }
        catch (HttpStatus ex) {
            reply = ex.route(resp);
        }
        else
        try {
            reply = route(session,req,resp);
        }
        catch (Exception ex) {
            resp.status = HttpResponse.ST_BAD_REQUEST;
            UsersLogin4Error error = new UsersLogin4Error();
            error.message = ex.getMessage();
            error.statusCode = 400;
            reply = error;
        }
        boolean dbg = false;

        write(resp,reply,dbg);
        if (reply != null) session.sendResponse(resp);
    }

    public static void main(String[] args) throws Exception {
        MatterFull.main(args);
    }
}
