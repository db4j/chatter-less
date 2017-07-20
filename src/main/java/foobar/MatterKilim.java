package foobar;

import static foobar.MatterLess.gson;
import static foobar.MatterLess.set;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import kilim.Pausable;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import mm.data.Channels;
import mm.data.Teams;
import mm.rest.TeamsNameExistsReps;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.UsersReps;
import org.db4j.Btree;
import org.db4j.Btrees;
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

    String getSession(HttpRequest req) {
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
    static String sep = "/";
    public static class Routes {
        String name = "/api/v4/teams/name";
        String umt = "/api/v4/users/me/teams/"; // jworc08ufivt7n9t287snjd781/channels/members";
        String xcm = "/channels/members";
        String xc = "/channels";
        String teams = "/api/v4/teams";
    }
    static Routes routes = new Routes();
    public static class Lengths {
        int name = routes.name.split(sep).length;
        int umt = routes.umt.split(sep).length;
        int xcm = 2;
        int xc = 1;
    }
    static Lengths lens = new Lengths();


    public Channels newChannel(String teamId) {
        Channels x = new Channels();
        x.createAt = x.updateAt = new java.util.Date().getTime();
        x.displayName = x.name = "town-square";
        x.id = matter.newid();
        x.teamId = teamId;
        return x;
    }
    
    static MatterData.FieldCopier<TeamsReqs,Teams> req2teams = new MatterData.FieldCopier(TeamsReqs.class,Teams.class);
    static MatterData.FieldCopier<Teams,TeamsReps> team2reps = new MatterData.FieldCopier(Teams.class,TeamsReps.class);
    public Object process(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        String uri = req.uriPath;
        String uid = getSession(req);
        String [] cmds = uri.split(sep);
        if (req.method.equals("GET") & uri.equals(routes.teams)) {
            // fixme - get the page and per_page values
            Object obj = db4j.submit(txn -> 
                    dm.teams.getall(txn).
                            vals())
                    .await().val.
                    stream().map(team2reps::copy).toArray();
            System.out.println("teams complete" + obj);
            System.out.println(gson.toJson(obj));
            return obj;
        }
        if (req.method.equals("POST") & uri.equals(routes.teams)) {
            String body = req.extractRange(req.contentOffset,req.contentOffset+req.contentLength);
            TeamsReqs treq = gson.fromJson(body,TeamsReqs.class);
            Teams team = req2teams.copy(treq);
            team.id = matter.newid();
            team.email = ""; // pull from user
            team.updateAt = team.createAt = new java.util.Date().getTime();
            Channels chan = newChannel(team.id);
            Integer result = db4j.submit(txn -> dm.addTeam(txn,team)).await().val;
            if (result==null)
                return "team already exists";
            db4j.submit(txn -> dm.addChan(txn,chan,result)).await();
            return team2reps.copy(team);
        }
        if (uri.startsWith(routes.umt)) {
            int len = cmds.length;
            if (len==lens.umt+lens.xc & uri.endsWith(routes.xc))
                return db4j.submit(txn -> {
                    String tid = cmds[lens.umt];
                    Integer kteam = dm.teamsById.find(txn,cmds[lens.umt]);
                    if (kteam==null) return null;
                    ArrayList<Channels> tt = new ArrayList();
                    Btree.Range<Btrees.II.Data> range = 
                            dm.chanByTeam.findPrefix(dm.chanByTeam.context().set(txn).set(kteam,0));
                            //getall(cc->cc.key).toArray(new Integer[0]);
                    while (range.next())
                        tt.add(dm.channels.find(txn,range.cc.val));
                    return tt;
                }).await().val;
            if (len==lens.umt+lens.xcm & uri.endsWith(routes.xcm))
                return new int[0];
            return new int[0];
        }
        if (uri.startsWith(routes.name)) {
            int len = lens.name;
            if (cmds.length==len+2 && cmds[len+1].equals("exists")) {
                Integer row = db4j.submit(txn -> 
                        dm.teamsByName.find(txn,cmds[len])
                ).await().val;
                return set(new TeamsNameExistsReps(), x->x.exists=row!=null);
            }
            return "";
        }
        if (true) return "";
        super.problem(resp, HttpResponse.ST_FORBIDDEN,
                "Only GET and HEAD accepted");
        return null;
    }
    public void write(HttpResponse resp,Object obj,boolean dbg) throws Exception {
        if (obj==null) return;
        String msg = (obj instanceof String) ? (String) obj : gson.toJson(obj);
        if (dbg)
            System.out.println("kilim.write: " + msg);
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
                boolean dbg = false;
                if (req.uriPath.equals(routes.teams))
                    dbg = true;

                write(resp,reply,dbg);
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
