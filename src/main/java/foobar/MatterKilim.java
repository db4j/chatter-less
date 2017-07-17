package foobar;

import static foobar.MatterLess.gson;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.examples.HttpFileServer;
import static kilim.examples.HttpFileServer.baseDirectory;
import static kilim.examples.HttpFileServer.baseDirectoryName;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpServer;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import kilim.nio.NioSelectorScheduler;
import mm.rest.UsersReps;
import org.srlutils.Rand;
import org.srlutils.Simple;

public class MatterKilim extends HttpSession {

    MatterLess matter;
    
    public static KeyValues formData(HttpRequest req) {
        String body = req.extractRange(req.contentOffset,req.contentOffset+req.contentLength);
        String b2 = null;
        try { b2 = java.net.URLDecoder.decode(body,"UTF-8"); }
        catch (Exception ex) {
            System.out.println("kws::formData -- failed to decode");
            try { b2 = java.net.URLDecoder.decode(body,"Latin1"); }
            catch (Exception e2) {}
        }
        KeyValues kvs = req.getQueryComponents(b2);
        return kvs;
    }
    
    static volatile int nkeep = 0;
    String expires(long days) {
        Date expdate= new Date();
        long ticks = expdate.getTime() + (days*24*3600*1000);
        expdate.setTime(ticks);
        DateFormat df = new SimpleDateFormat("dd MMM yyyy kk:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        String cookieExpire = "expires=" + df.format(expdate);
        return cookieExpire;
    }

    static String parse(String sub,String name) {
        return sub.startsWith(name) ? sub.substring(name.length()) : null;
    }

    String getSession(HttpRequest req,HttpResponse resp) {
        String cookie = req.getHeader("Cookie");
        String uid = null;
        boolean dbg = false;
        if (cookie != null && !cookie.isEmpty()) {
            String [] lines = cookie.split("; *");
            for (int ii = 0; ii < lines.length; ii++) {
                String sub = lines[ii];
                if (dbg) System.out.println(sub);
                if (ii+1 < lines.length && lines[ii+1].startsWith("Expires=")) {
                    if (dbg) System.out.println(lines[ii+1]);
                    ii++;
                }
                String part;
                if ((part=parse(sub,MatterLess.mmuserid+"=")) != null)
                    uid = part;
            }
        }
        return uid;
    }
    public Object process(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        if (req.uriPath.startsWith("/api/v4/teams/name/harbor/exists")) {
            return "{\"exists\":false}";
        }
        if (req.uriPath.startsWith("/api/v4/teams/name/harbor2/exists2")) {
            KeyValues kvs = formData(req);
            String cmd = kvs.get( "command" );
            UsersReps msg = gson.fromJson(cmd,UsersReps.class);
            Object rep = new Object();
            if (false)
                resp.status = HttpResponse.ST_UNAUTHORIZED;
            OutputStream out = resp.getOutputStream();
            String txt = gson.toJson( rep );
            out.write( txt.getBytes() );
            sendResponse(resp);
        } else {
            super.problem(resp, HttpResponse.ST_FORBIDDEN,
                    "Only GET and HEAD accepted");
        }
        return null;
    }
    public void write(HttpResponse resp,Object obj) throws Exception {
        if (obj==null) return;
        String msg = (obj instanceof String) ? (String) obj : gson.toJson(obj);
        resp.getOutputStream().write(msg.getBytes());
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

                System.out.println("kilim: "+req.uriPath);
                Object reply = process(req,resp);
                write(resp,reply);
                sendResponse(resp);
                

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
        catch (Exception ex) {
            System.out.println("DiaryKws:exception -- " + ex);
            ex.printStackTrace();
        }
        super.close();
    }

    public static void main(String[] args) throws Exception {
        MatterLess.main(args);
    }
}
