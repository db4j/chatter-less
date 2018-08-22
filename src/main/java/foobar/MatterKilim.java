package foobar;

import static foobar.Utilmm.*;
import java.io.File;
import java.util.function.Consumer;
import kilim.Pausable;
import kilim.http.MimeTypes;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import mm.data.Sessions;
import mm.rest.UsersLogin4Error;
import org.db4j.Bmeta;
import org.db4j.Db4jMvc;

public class MatterKilim extends Db4jMvc {
    MatterControl matter;
    boolean yoda = true;
    { gson = MatterControl.gson; }

    MatterKilim(MatterControl $matter) {
        super(x -> new MatterRoutes(x).setup($matter),pp -> pp.auth());
        matter = $matter;
    }
    
    public static class AuthRouter<PP extends AuthRouter> extends Db4jRouter<PP> {
        MatterControl matter;
        MatterData dm;
        MatterWebsocket ws;
        String uid;
        Sessions mmauth;
        Integer kauth;
        String etag;
        { logExtra = true; }

        AuthRouter(Consumer<Route> mk) { super(mk); }

        PP setup(MatterControl $matter) {
            matter = $matter;
            setup(matter.db4j);
            dm = matter.dm;
            ws = matter.ws;
            return (PP) this;
        }

        <KK,VV> VV get(Bmeta<?,KK,VV,?> map,KK key) throws Pausable {
            return db4j.submit(txn -> map.find(txn,key)).await().val;
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
    }
    static File urlToPath(HttpRequest req) {
        String base = "./mattermost/webapp/dist";
        String uri = req.uriPath;
        String path = (uri!=null && uri.startsWith("/static/")) ? uri.replace("/static",""):"/root.html";
        return new File(base+path);
    }
    public Object handleEx(Session session,HttpRequest req,HttpResponse resp,Exception ex) {
        if (ex instanceof HttpStatus)
            return ((HttpStatus) ex).route(resp);
        resp.status = HttpResponse.ST_BAD_REQUEST;
        UsersLogin4Error error = new UsersLogin4Error();
        error.message = ex.getMessage();
        error.statusCode = 400;
        return error;
    }
    public void handle(Session session,HttpRequest req,HttpResponse resp) throws Pausable, Exception {
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
            String contentType = MimeTypes.mimeType(file);
            int sent = session.sendFile(req,resp,file,contentType);
            if (sent > 0) session.problem(resp,HttpResponse.ST_NOT_FOUND,"unable to send file");
        }
        else
            super.handle(session,req,resp);
    }

    public static void main(String[] args) throws Exception {
        MatterFull.main(args);
    }
}
