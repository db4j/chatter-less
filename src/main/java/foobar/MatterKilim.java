package foobar;

import static foobar.MatterLess.gson;
import static foobar.MatterLess.mmuserid;
import static foobar.MatterLess.req2users;
import static foobar.MatterLess.set;
import static foobar.MatterLess.users2reps;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Consumer;
import javax.servlet.http.HttpServletResponse;
import kilim.Pausable;
import kilim.Task;
import static kilim.examples.HttpFileServer.mimeType;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.rest.ChannelsReps;
import mm.rest.ChannelsxMembersReps;
import mm.rest.LicenseClientFormatOldReps;
import mm.rest.PreferencesSaveReq;
import mm.rest.TeamsNameExistsReps;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.UsersLogin4Error;
import mm.rest.UsersLogin4Reqs;
import mm.rest.UsersLoginReqs;
import mm.rest.UsersReqs;
import org.db4j.Bmeta;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;
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
    static String expires(double days) {
        Date expdate= new Date();
        long ticks = expdate.getTime() + (long)(days*24*3600*1000);
        expdate.setTime(ticks);
        DateFormat df = new SimpleDateFormat("dd MMM yyyy kk:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        String cookieExpire = "expires=" + df.format(expdate);
        return cookieExpire;
    }

    static void setCookie(HttpResponse resp,String name,String value,double days) {
        String expires = expires(days);
        String newcookie = String.format("%s=%s; %s",name,value,expires);
        System.out.println("Set-Cookie: "+newcookie);
        resp.addField("Set-Cookie",newcookie);
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
        String auth = req.getHeader("authorization");
        String [] words = null;
        if (uid==null && auth != null) {
            words = auth.split(" ");
            if (words.length > 1 && words[0].equals("BEARER"))
                uid = words[1];
        }
        return uid;
    }


    
    static String wildcard = "?";
    public class Router {
    }
    public static class Route {
        String [] parts;
        Routeable handler;
        Route(String uri,Routeable $handler) {
            parts = uri.split(sep);
            handler = $handler;
            for (int ii=1; ii < parts.length; ii++)
                if (parts[ii].startsWith(wildcard)) parts[ii] = wildcard;
        }
        String [] test(String [] uri,HttpRequest req) {
            String [] keys = new String[uri.length];
            boolean test = uri.length==parts.length;
            int num = 0;
            for (int ii=0; test & ii < parts.length; ii++)
                if (parts[ii]==wildcard)
                    keys[num++] = uri[ii];
                else
                    test &= parts[ii].equals(uri[ii]);
            return test ? keys:null;
        }
        Route set(Factory factory) {
            handler = factory;
            return this;
        }
    }
    ArrayList<Route> route = new ArrayList();

    interface Routeable { default Object run(String [] keys) { return null; } };
    interface Routeable0 extends Routeable { Object accept() throws Pausable,Exception; }
    interface Routeable1 extends Routeable { Object accept(String s1) throws Pausable,Exception; }
    interface Routeable2 extends Routeable { Object accept(String s1,String s2) throws Pausable,Exception; }
    interface Routeable3 extends Routeable { Object accept(String s1,String s2,String s3) throws Pausable,Exception; }
    interface Fullable0  extends Routeable { Object accept(HttpRequest req,HttpResponse resp) throws Pausable,Exception; }
    interface Factory<TT extends Routeable> extends Routeable { TT make(Processor pp); }

    Object route(HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        String path[] = req.uriPath.split(sep), keys[] = null;
        Route rr = null;
        for (Route r2 : route)
            if ((keys = (rr=r2).test(path,req)) != null)
                break;
        if (keys != null)
            return route(rr.handler,keys,req,resp);
        return null;
    }
    Object route(Routeable hh,String [] keys,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Processor pp = new Processor(req,resp);
        if (hh instanceof Routeable0) return ((Routeable0) hh).accept();
        if (hh instanceof Routeable1) return ((Routeable1) hh).accept(keys[0]);
        if (hh instanceof Routeable2) return ((Routeable2) hh).accept(keys[0],keys[1]);
        if (hh instanceof Routeable3) return ((Routeable3) hh).accept(keys[0],keys[1],keys[2]);
        if (hh instanceof Factory)
            return route(((Factory) hh).make(pp),keys,req,resp);
        return hh.run(keys);
    }

    void add(Route rr) {
        route.add(rr);
    }
    
    void add(String uri,Routeable0 rr) { add(new Route(uri,rr)); }
    void add(String uri,Routeable1 rr) { add(new Route(uri,rr)); }
    void add(String uri,Routeable2 rr) { add(new Route(uri,rr)); }
    void add(String uri,Routeable3 rr) { add(new Route(uri,rr)); }

    void make0(String uri,Factory<Routeable0> ff) { add(new Route(uri,ff)); }
    void make1(String uri,Factory<Routeable1> ff) { add(new Route(uri,ff)); }
    void make2(String uri,Factory<Routeable2> ff) { add(new Route(uri,ff)); }
    void make3(String uri,Factory<Routeable3> ff) { add(new Route(uri,ff)); }

    public void sendFile(HttpResponse resp,File file,boolean headOnly) throws IOException, Pausable {
        FileInputStream fis;
        FileChannel fc;

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
            endpoint.write(fc, 0, file.length());
        } finally {
            fc.close();
            fis.close();
        }
    }
    
    
    public class Processor {
        Processor() {}
        Processor(HttpRequest $req,HttpResponse $resp) {
            req = $req;
            resp = $resp;
            uri = req.uriPath;
            uid = getSession(req);
        }
        HttpRequest req;
        HttpResponse resp;
        String uri;
        String uid;
        
        String body() {
            return req.extractRange(req.contentOffset,req.contentOffset+req.contentLength);
        }
        
        
        { if (first) make0("/api/v4/config/client",self -> self::config); }
        public Object config() throws IOException, Pausable {
            File file = new File("config.json");
            sendFile(resp,file,false);
            return null;
        }
        { if (first) make1("/api/foobar",self -> self::members); }
        public Object members(String teamid) throws Pausable {
            return req.uriPath;
        }

        { if (first) make0(matter.routes.users,self -> self::users); }
        public Object users() throws Pausable {
            UsersReqs ureq = gson.fromJson(body(),UsersReqs.class);
            Users u = req2users.copy(ureq,new Users());
            u.id = matter.newid();
            u.password = matter.salt(ureq.password);
            u.updateAt = u.lastPasswordUpdate = u.createAt = new java.util.Date().getTime();
            u.roles = "system_user";
            u.notifyProps = null; // new NotifyUsers().init(rep.username);
            u.locale = "en";
//            u.authData=u.authService=u.firstName=u.lastName=u.nickname=u.position="";
            Query query = db4j.submitCall(txn -> {
                int row = dm.idcount.plus(txn,1);
                dm.users.insert(txn,row,u);
                dm.idmap.insert(txn,u.id,row);
                dm.usersByName.insert(txn,u.username,row);
                System.out.println("users.insert: " + u.id + " -- " + row);
            }).await();
            if (query.getEx() != null) {
                System.out.println(query.getEx());
                query.getEx().printStackTrace();
                return "an error occurred";
            }
            return users2reps.copy(u);
        }

        { if (first) make0(matter.routes.login,self -> self::login); }
        { if (first) make0(matter.routes.login4,self -> self::login); }
        public Object login() throws Pausable {
            boolean v4 = uri.equals(matter.routes.login4);
            UsersLoginReqs login = v4 ? null : gson.fromJson(body(),UsersLoginReqs.class);
            UsersLogin4Reqs login4 = !v4 ? null : gson.fromJson(body(),UsersLogin4Reqs.class);
            String password = v4 ? login4.password : login.password;
            Users user = db4j.submit(txn -> {
                Integer row;
                if (login4==null)
                    row = dm.idmap.find(txn,login.id);
                else row = dm.usersByName.find(txn,login4.loginId);
                if (row==null) {
                    matter.print(login);
                    matter.print(login4);
                }
                return row==null ? null : dm.users.find(txn,row);
            }).await().val;
            if (user==null || ! user.password.equals(password)) {
                String msg = user==null ? "user not found" : "invalid password";
                resp.status = HttpResponse.ST_BAD_REQUEST;
                return msg;
            }
            else {
                // fixme::fakeSecurity - add auth token (and check for it on requests)
                setCookie(resp,matter.mmuserid,user.id,30.0);
                setCookie(resp,matter.mmauthtoken,user.id,30.0);
                resp.addField("Token",user.id);
                return users2reps.copy(user);
            }
        }
        
        { if (first) make0(matter.routes.um,self -> self::um); }
        public Object um() throws Pausable {
            Users user = db4j.submit(txn -> {
                Integer row = dm.idmap.find(txn,uid);
                return row==null ? null : dm.users.find(txn,row);
            }).await().val;
            if (user==null)
                return setProblem(resp,HttpResponse.ST_BAD_REQUEST,"user not found");
            return users2reps.copy(user);
        }        
        
        { if (first) make1(routes.umtxcm,self -> self::umtxcm); }
        public Object umtxcm(String teamid) throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            ArrayList<Integer> kcembers = db4j.submit(txn ->
                    dm.cemberMap.findPrefix(
                            dm.cemberMap.context().set(txn).set(kuser,0)
                    ).getall(cc -> cc.val)).await().val;

            Spawner<ChannelsxMembersReps> tasker = new Spawner();
            for (Integer kcember : kcembers) tasker.spawn(() -> {
                ChannelMembers cember = get(dm.cembers,kcember);
                Channels channel = get(dm.channels,cember.channelId);
                return channel.teamId.equals(teamid) ? cember2reps.copy(cember) : null;
            });
            return tasker.join();            
        }        

        { if (first) make0(matter.routes.ump,self -> () ->
                new Object[] { set(new PreferencesSaveReq(),
                        x -> { x.category="tutorial_step"; x.name = x.userId = uid; x.value = "0"; }) });
        }
    }
    private boolean first = true;
    {
        System.out.println("kilim.init");
        new Processor();
        first = false;
    }

    <KK,VV> VV get(Bmeta<?,KK,VV,?> map,KK key) throws Pausable {
        return db4j.submit(txn -> map.find(txn,key)).await().val;
    }
    <TT> TT get(Btrees.IK<TT> map,String key) throws Pausable {
        return db4j.submit(txn -> dm.get(txn,map,key)).await().val;
    }
    <TT> TT get2(Btrees.IK<TT> map,int key) throws Pausable {
        return db4j.submit(txn -> map.find(txn,key)).await().val;
    }
    <TT> TT get(Db4j.Utils.QueryFunction<TT> body) throws Pausable {
        return db4j.submit(body).await().val;
    }
    
    static class Spawner<TT> {
        ArrayList<Spawn<TT>> tasks = new ArrayList();
        Spawn<TT> spawn(kilim.Spawnable<TT> body) {
            Spawn task = Task.spawn(body);
            tasks.add(task);
            return task;
        }
        ArrayList<TT> join() throws Pausable {
            ArrayList<TT> vals = new ArrayList();
            for (Spawn<TT> task : tasks)
                vals.add(task.mb.get());
            return vals;
        }
    }
    static class Tasker<TT extends Task> {
        ArrayList<TT> tasks = new ArrayList();
        TT spawn(kilim.Spawnable.Call body) {
            TT task = (TT) Task.spawnCall(body);
            tasks.add(task);
            return task;
        }
        <AA> TT beget1(AA arg1,kilim.Spawnable.Call1<AA> body) {
            TT task = (TT) Task.beget1(arg1,body);
            tasks.add(task);
            return task;
        }
        ArrayList<TT> join() throws Pausable {
            for (TT task : tasks)
                task.join();
            return tasks;
        }
    }
    
    public Object setProblem(HttpResponse resp, byte[] statusCode, String msg) {
        resp.status = statusCode;
        return msg;
    }
    
    
    { add("/api/v4/users/me/teams/?tid/channels/members",teamid -> new int[0]); }
    { add("/api/v4/users/me/teams/?tid/channels",this::channels); }
    public Object channels(String teamid) throws Pausable {
        return db4j.submit(txn -> {
            Integer kteam = dm.idmap.find(txn,teamid);
            if (kteam==null) return null;
            ArrayList<Channels> tt = new ArrayList();
            Btree.Range<Btrees.II.Data> range = 
                    dm.chanByTeam.findPrefix(dm.chanByTeam.context().set(txn).set(kteam,0));
                    //getall(cc->cc.key).toArray(new Integer[0]);
            while (range.next())
                tt.add(dm.channels.find(txn,range.cc.val));
            return tt;
        }).await().val;
    }



    
    static String sep = "/";
    public static class Routes {
        String name = "/api/v4/teams/name";
        String umt = "/api/v4/users/me/teams/"; // jworc08ufivt7n9t287snjd781/channels/members";
        String umtxcm = "/api/v4/users/me/teams/?teamid/channels/members";
        String xcm = "/channels/members";
        String xc = "/channels";
        String teams = "/api/v4/teams";
        String cmmv = "/api/v4/channels/members/me/view";
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
    static MatterData.FieldCopier<ChannelMembers,ChannelsxMembersReps> cember2reps =
            new MatterData.FieldCopier(ChannelMembers.class,ChannelsxMembersReps.class);
    public Object process(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        String uri = req.uriPath;
        
        Object robj = route(req,resp);
        if (robj != null) return robj;
        
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
            String uid = getSession(req);
            String body = req.extractRange(req.contentOffset,req.contentOffset+req.contentLength);
            TeamsReqs treq = gson.fromJson(body,TeamsReqs.class);
            Teams team = req2teams.copy(treq);
            team.id = matter.newid();
            team.email = ""; // pull from user
            team.updateAt = team.createAt = new java.util.Date().getTime();
            Channels chan = newChannel(team.id);
            ChannelMembers cm = new ChannelMembers();
            cm.userId = uid;
            cm.channelId = chan.id;
            cm.roles = "channel_user";
            TeamMembers tm = new TeamMembers();
            tm.userId = uid;
            tm.teamId = team.id;
            tm.roles = "team_user";
            Integer kuser = db4j.submit(txn -> dm.idmap.find(txn,uid)).await().val;
            Integer result = db4j.submit(txn -> {
                Integer kteam = dm.addTeam(txn,team);
                if (kteam==null) return null;
                int kchan = dm.addChan(txn,chan,kteam);
                dm.addTeamMember(txn,kuser,tm);
                dm.addChanMember(txn,kuser,cm);
                return kteam;
            }).await().val;
            if (result==null)
                return "team already exists";
            return team2reps.copy(team);
        }
        if (uri.equals("/api/seth/channels")) {
            MatterLess.print(db4j.submit(txn -> dm.channels.getall(txn).vals()).await().val);
            return "channels printed";
        }
        if (uri.equals(routes.cmmv)) {
            return set(new ChannelsReps.View(),x->x.status="OK");
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
        if (uri.equals(matter.routes.license))
            return set(new LicenseClientFormatOldReps(),x->x.isLicensed="false");
        if (uri.equals("/api/v3/users/websocket"))
            return "not available";
        return new int[0];
    }
    public void write(HttpResponse resp,Object obj,boolean dbg) throws IOException {
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
                Object reply = null;
                try {
                    reply = process(req,resp);
                }
                catch (Exception ex) {
                    resp.status = HttpResponse.ST_INTERNAL_SERVER_ERROR;
                    reply = ex.toString();
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ex.printStackTrace(pw);
                    reply += sw.toString();
                    pw.close();
                }
                boolean dbg = false;
                if (req.uriPath.equals(routes.teams))
                    dbg = true;

                write(resp,reply,dbg);
                if (reply != null) sendResponse(resp);
                

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
        JettyLooper.main(args);
    }
}
