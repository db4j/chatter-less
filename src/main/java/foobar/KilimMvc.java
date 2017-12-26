package foobar;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.function.Consumer;
import kilim.Pausable;
import static kilim.examples.HttpFileServer.mimeType;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import org.srlutils.Simple;

public class KilimMvc {
    static String sep = "/";
    static String qsep = "\\?";
    static String wildcard = "{";
    static String asterisk = "*";
    ArrayList<Route> route = new ArrayList();
    Route fallback;

    
    public static class Route {
        String method;
        String [] parts;
        boolean varquer;
        String [] queries = new String[0];
        Routeable handler;
        Scannable<? extends P1> source;
        Preppable prep;
        String uri;
        boolean skip;
        
        Route(String $uri,Routeable $handler) {
            uri = $uri;
            String [] pieces = uri.split(qsep,2);
            parts = pieces[0].split(sep);
            if (pieces.length > 1) {
                String [] qo = queries = pieces[1].split(sep);
                if (varquer = qo[qo.length-1].equals(asterisk))
                    queries = java.util.Arrays.copyOf(qo,qo.length-1);
            }
            handler = $handler;
            for (int ii=1; ii < parts.length; ii++)
                if (parts[ii].startsWith(wildcard)) parts[ii] = wildcard;
        }
        Route(String $method,String $uri,Routeable $handler) {
            this($uri,$handler);
            method = $method;
        }
        Route(String $method,String $uri) {
            this($method,$uri,null);
            method = $method;
        }
        Route() { skip = true; }
        /** for debugging only */
        boolean test(HttpRequest req) {
            Route.Info info = new Route.Info(req);
            return test(info,req);
        }
        boolean test(Info info,HttpRequest req) {
            if (info.parts.length != parts.length)
                return false;
            if (method != null && ! method.equals(req.method))
                return false;
            int num = 0;
            for (int ii=0; ii < parts.length; ii++)
                if (parts[ii]==wildcard)
                    info.keys[num++] = info.parts[ii];
                else if (! parts[ii].equals(info.parts[ii]))
                    return false;
            
            if (varquer==false & info.queries.count != queries.length)
                return false;
            for (String query : queries)
                if ((info.keys[num++] = info.get(query)) == null)
                    return false;
            return true;
        }
        Route set(Factory factory) {
            handler = factory;
            return this;
        }
        Route skip() { skip = true; return this; }
        public static class Info {
            String [] parts;
            String [] keys;
            KeyValues queries;
            String get(String query) {
                // queries.get conflates a missing key with a missing value, ie both are ""
                int index = queries.indexOf(query);
                return index < 0 ? null : queries.values[index];
            }
            Info(HttpRequest req) {
                parts = req.uriPath.split(sep);
                queries = req.getQueryComponents();
                keys = new String[parts.length + queries.keys.length];
            }
        }
    }

    // fixme:kilim - overriding a default method appears to cause kilim to weave incorrectly
    interface Routeable { default Object run(String [] keys) { return null; } };
    interface Routeable0 extends Routeable { Object accept() throws Pausable,Exception; }
    interface Routeable1 extends Routeable { Object accept(String s1) throws Pausable,Exception; }
    interface Routeable2 extends Routeable { Object accept(String s1,String s2) throws Pausable,Exception; }
    interface Routeable3 extends Routeable { Object accept(String s1,String s2,String s3) throws Pausable,Exception; }
    interface Routeable4 extends Routeable { Object accept(String s1,String s2,String s3,String s4) throws Pausable,Exception; }
    interface Routeable5 extends Routeable { Object accept(String s1,String s2,String s3,String s4,String s5) throws Pausable,Exception; }
    interface Routeablex extends Routeable { Object accept(String [] keys) throws Pausable,Exception; }
    interface Fullable0  extends Routeable { Object accept(HttpRequest req,HttpResponse resp) throws Pausable,Exception; }
    interface Factory<TT extends Routeable,PP extends P1> extends Routeable { TT make(PP pp); }

    void checkRoute(Route r2) {
        int limit = 10;
        Routeable rr = r2.handler;
        for (int ii=0; rr instanceof Factory; ii++) {
            if (ii > limit)
                throw new RuntimeException("route factory recursion limit exceeded: "+r2);
            P1 pp = r2.source.supply(null);
            pp.init(null,null,null);
            rr = ((Factory) rr).make(pp);
        }
        boolean known =
                rr instanceof Routeable0 |
                rr instanceof Routeable1 |
                rr instanceof Routeable2 |
                rr instanceof Routeable3 |
                rr instanceof Routeable4 |
                rr instanceof Routeable5 |
                rr instanceof Routeablex;
        if (!known)
            throw new RuntimeException("no known routing available: "+r2);
    }
    
    Object route(Session session,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Route.Info info = new Route.Info(req);
        for (int ii=0; ii < route.size(); ii++) {
            Route r2 = route.get(ii);
            if (r2.test(info,req))
                return route(null,session,r2,r2.handler,info.keys,req,resp);
        }
        return route(null,session,fallback,fallback.handler,info.keys,req,resp);
    }
    Object route(Routeable hh,String [] keys) throws Pausable,Exception {
        if (hh instanceof Routeable0) return ((Routeable0) hh).accept();
        if (hh instanceof Routeable1) return ((Routeable1) hh).accept(keys[0]);
        if (hh instanceof Routeable2) return ((Routeable2) hh).accept(keys[0],keys[1]);
        if (hh instanceof Routeable3) return ((Routeable3) hh).accept(keys[0],keys[1],keys[2]);
        if (hh instanceof Routeable4) return ((Routeable4) hh).accept(keys[0],keys[1],keys[2],keys[3]);
        if (hh instanceof Routeable5) return ((Routeable5) hh).accept(keys[0],keys[1],keys[2],keys[3],keys[4]);
        if (hh instanceof Routeablex) return ((Routeablex) hh).accept(keys);
        return hh.run(keys);
    }
    Object route(P1 pp,Session session,Route r2,Routeable hh,String [] keys,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        if (pp==null)
            pp = r2.source.supply(null);
        if (hh instanceof Factory) {
            pp.init(session,req,resp);
            if (r2.prep != null)
                r2.prep.accept(pp);
            Routeable h2 = ((Factory) hh).make(pp);
            return route(pp,session,r2,h2,keys,req,resp);
        }
        return route(hh,keys);
    }

    // unused but useful for debugging routing problems
    /**
     * filter the registered routes that match the request
     * @param req the request to test the routes against
     * @return the indices of the matching routes
     */
    ArrayList<Integer> filterRoutes(HttpRequest req) {
        Route.Info info = new Route.Info(req);
        ArrayList<Integer> keys = new ArrayList();
        for (int ii=0; ii < route.size(); ii++)
            if (route.get(ii).test(info,req)) keys.add(ii);
        return keys;
    }
    
    interface Preppable<PP> { void accept(PP val) throws Pausable; }
    interface Scannable<PP extends P1> { PP supply(Consumer<Route> router); }
    
    <PP extends P1> PP scan(Scannable<PP> source,Preppable<PP> auth) {
        ArrayList<Route> local = new ArrayList();
        PP pp = source.supply(rr -> local.add(rr));
        for (Route rr : local)
            addRoute(rr,sink -> pp,source,auth);
        return pp;
    }

    <PP extends P1> void addRoute(Route rr,Scannable<PP> direct,Scannable<PP> source,Preppable<PP> auth) {
        if (rr.handler instanceof Factory) {
            rr.source = source;
            rr.prep = (Preppable<P1>) auth;
        }
        else
            rr.source = direct;
        checkRoute(rr);
        if (!rr.skip)
            route.add(rr);
    }
    
    public static class P1<PP extends P1> {
        boolean first;
        private Consumer<Route> mk;
        Session session;
        HttpRequest req;
        HttpResponse resp;

        P1(Consumer<Route> mk) {
            this.mk = mk;
            first = mk != null;
        }
        void init(Session $session,HttpRequest $req,HttpResponse $resp) {
            session = $session;
            req = $req;
            resp = $resp;
        }
        void add(Route rr) {
            if (first)
                mk.accept(rr);
        }

        void add(String uri,Routeable0 rr) { add(new Route(uri,rr)); }
        void add(String uri,Routeable1 rr) { add(new Route(uri,rr)); }
        void add(String uri,Routeable2 rr) { add(new Route(uri,rr)); }
        void add(String uri,Routeable3 rr) { add(new Route(uri,rr)); }
        void add(String uri,Routeable4 rr) { add(new Route(uri,rr)); }
        void add(String uri,Routeable5 rr) { add(new Route(uri,rr)); }

        void make0(String uri,Factory<Routeable0,PP> ff) { add(new Route(uri,ff)); }
        void make1(String uri,Factory<Routeable1,PP> ff) { add(new Route(uri,ff)); }
        void make2(String uri,Factory<Routeable2,PP> ff) { add(new Route(uri,ff)); }
        void make3(String uri,Factory<Routeable3,PP> ff) { add(new Route(uri,ff)); }
        void make4(String uri,Factory<Routeable4,PP> ff) { add(new Route(uri,ff)); }
        void make5(String uri,Factory<Routeable5,PP> ff) { add(new Route(uri,ff)); }

        void make0(Route route,Factory<Routeable0,PP> ff) { add(route.set(ff)); }
        void make1(Route route,Factory<Routeable1,PP> ff) { add(route.set(ff)); }
        void make2(Route route,Factory<Routeable2,PP> ff) { add(route.set(ff)); }
        void make3(Route route,Factory<Routeable3,PP> ff) { add(route.set(ff)); }
        void make4(Route route,Factory<Routeable4,PP> ff) { add(route.set(ff)); }
        void make5(Route route,Factory<Routeable5,PP> ff) { add(route.set(ff)); }

    }
    
    public void sendJson(HttpResponse resp,byte [] msg) throws IOException {
        // fixme -- this appears to block for long messages
        resp.setContentType("application/json");
        resp.getOutputStream().write(msg);
    }
    public interface KilimHandler {
        public void handle(Session session,HttpRequest req,HttpResponse resp) throws Pausable, Exception;
    }
    public static class Session extends HttpSession {
        KilimHandler handler;
        Session(KilimHandler handler) { this.handler = handler; }
        protected Session() {}
        public void handle(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
            handler.handle(this,req,resp);
        }
    public void execute() throws Pausable, Exception {
        try {
            // We will reuse the req and resp objects
            HttpRequest req = new HttpRequest();
            HttpResponse resp = new HttpResponse();
            while (true) {
                super.readRequest(req);
                if (req.keepAlive())
                    resp.addField("Connection", "Keep-Alive");

                handle(req,resp);

                if (!req.keepAlive()) 
                    break;
                else
                    Simple.nop();
            }
        } catch (EOFException e) {
//                System.out.println("[" + this.id + "] Connection Terminated " + nkeep);
        } catch (IOException ioe) {
            System.out.println("[" + this.id + "] IO Exception:" + ioe.getMessage());
        }
        super.close();
    }
    public void sendFile(HttpRequest req,HttpResponse resp,File file) throws IOException, Pausable {
        FileInputStream fis;
        FileChannel fc;
        boolean headOnly = req.method.equals("HEAD");

        try {
            fis = new FileInputStream(file);
            fc = fis.getChannel();
        } catch (IOException ioe) {
            problem(resp, HttpResponse.ST_NOT_FOUND, "File not found...Send exception: " + ioe.getMessage());
            return;
        }
        try {
            String contentType = mimeType(file);
            if (contentType != null) {
                resp.setContentType(contentType);
            }
            resp.setContentLength(file.length());
            // Send the header first (with the content type and length)
            super.sendResponse(resp);
            // Send the contents; this uses sendfile or equivalent underneath.
            if (!headOnly)
                endpoint.write(fc, 0, file.length());
        } finally {
            fc.close();
            fis.close();
        }
    }
    }
}
