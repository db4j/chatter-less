package foobar;

import static foobar.MatterLess.gson;
import static foobar.MatterLess.set;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import kilim.Pausable;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import mm.data.Teams;
import mm.rest.TeamsNameExistsReps;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.UsersReps;
import org.db4j.Db4j;
import org.srlutils.Simple;

public class MatterKilim extends HttpSession {

    MatterLess matter;
    Db4j db4j;
    MatterData dm;

    void setup(MatterLess $matter) {
        matter = $matter;
        db4j = matter.db4j;
        dm = matter.dm;
    }
    
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
    static MatterData.FieldCopier<TeamsReqs,Teams> req2teams = new MatterData.FieldCopier(TeamsReqs.class,Teams.class);
    static MatterData.FieldCopier<Teams,TeamsReps> team2reps = new MatterData.FieldCopier(Teams.class,TeamsReps.class);
    public Object process(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        String [] cmds = req.uriPath.split("/");
        if (req.uriPath.equals("/api/v4/teams")) {
            String body = req.extractRange(req.contentOffset,req.contentLength);
            TeamsReqs treq = gson.fromJson(body,TeamsReqs.class);
            Teams team = req2teams.copy(treq);
            Integer result = db4j.submit(txn -> {
                Integer row = dm.teamsByName.find(txn,treq.name);
                if (row==null) {
                    int newrow = dm.teamCount.plus(txn,1);
                    dm.teams.insert(txn,newrow,team);
                    dm.teamsByName.insert(txn,team.name,newrow);
                    return newrow;
                }
                return null;
            }).await().val;
            return result==null ? team2reps.copy(team):"team already exists";
        }
        if (req.uriPath.startsWith("/api/v4/teams/name")) {
            if (cmds.length==7 && cmds[6].equals("exists"))
                return set(new TeamsNameExistsReps(), x->x.exists=false);
            return "";
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
