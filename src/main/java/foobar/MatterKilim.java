package foobar;

import static foobar.MatterLess.gson;
import static foobar.MatterLess.req2users;
import static foobar.MatterLess.set;
import static foobar.MatterLess.users2reps;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.function.Function;
import kilim.Pausable;
import kilim.Task;
import static kilim.examples.HttpFileServer.mimeType;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.Posts;
import mm.data.Status;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.rest.ChannelsReps;
import mm.rest.ChannelsxMembersReps;
import mm.rest.ChannelsxStatsReps;
import mm.rest.LicenseClientFormatOldReps;
import mm.rest.PreferencesSaveReq;
import mm.rest.TeamsAddUserToTeamFromInviteReqs;
import mm.rest.TeamsMembersRep;
import mm.rest.TeamsNameExistsReps;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.TeamsUnreadRep;
import mm.rest.TeamsxChannelsxPostsCreateReqs;
import mm.rest.TeamsxChannelsxPostsPage060Reps;
import mm.rest.UsersLogin4Reqs;
import mm.rest.UsersLoginReqs;
import mm.rest.UsersReqs;
import mm.rest.UsersStatusIdsRep;
import mm.rest.Xxx;
import org.db4j.Bmeta;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;
import org.db4j.Db4j.Transaction;
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
        Date expdate = new Date();
        long ticks = expdate.getTime() + (long)(days*24*3600*1000);
        expdate.setTime(ticks);
        DateFormat df = new SimpleDateFormat("dd MMM yyyy kk:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        String cookieExpire = "expires=" + df.format(expdate);
        return cookieExpire;
    }

    static void setCookie(HttpResponse resp,String name,String value,double days,boolean httponly) {
        String expires = expires(days);
        String newcookie = String.format("%s=%s; Path=/; %s",name,value,expires);
        if (httponly) newcookie += "; HttpOnly";
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


    
    static String wildcard = "{";
    public class Router {
    }
    public static class Route {
        String method;
        String [] parts;
        String [] queries;
        Routeable handler;
        String uri;
        Route(String $uri,Routeable $handler) {
            uri = $uri;
            String [] pieces = uri.split(qsep,2);
            parts = pieces[0].split(sep);
            queries = pieces.length > 1 ? pieces[1].split(sep):new String[0];
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
            for (String query : queries)
                if ((info.keys[num++] = info.queries.get(query)).length()==0)
                    return false;
            return true;
        }
        Route set(Factory factory) {
            handler = factory;
            return this;
        }
        public static class Info {
            String [] parts;
            String [] keys;
            KeyValues queries;
            Info(HttpRequest req) {
                parts = req.uriPath.split(sep);
                queries = req.getQueryComponents();
                keys = new String[parts.length + queries.keys.length];
            }
        }
    }
    ArrayList<Route> route = new ArrayList();

    interface Routeable { default Object run(String [] keys) { return null; } };
    interface Routeable0 extends Routeable { Object accept() throws Pausable,Exception; }
    interface Routeable1 extends Routeable { Object accept(String s1) throws Pausable,Exception; }
    interface Routeable2 extends Routeable { Object accept(String s1,String s2) throws Pausable,Exception; }
    interface Routeable3 extends Routeable { Object accept(String s1,String s2,String s3) throws Pausable,Exception; }
    interface Routeable4 extends Routeable { Object accept(String s1,String s2,String s3,String s4) throws Pausable,Exception; }
    interface Routeable5 extends Routeable { Object accept(String s1,String s2,String s3,String s4,String s5) throws Pausable,Exception; }
    interface Fullable0  extends Routeable { Object accept(HttpRequest req,HttpResponse resp) throws Pausable,Exception; }
    interface Factory<TT extends Routeable> extends Routeable { TT make(Processor pp); }

    static final Object routeNotFound = new Object();
    
    Object route(HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Route.Info info = new Route.Info(req);
        for (Route r2 : route)
            if (r2.test(info,req))
                return route(r2.handler,info.keys,req,resp);
        return routeNotFound;
    }
    Object route(Routeable hh,String [] keys,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Processor pp = new Processor(req,resp);
        if (hh instanceof Routeable0) return ((Routeable0) hh).accept();
        if (hh instanceof Routeable1) return ((Routeable1) hh).accept(keys[0]);
        if (hh instanceof Routeable2) return ((Routeable2) hh).accept(keys[0],keys[1]);
        if (hh instanceof Routeable3) return ((Routeable3) hh).accept(keys[0],keys[1],keys[2]);
        if (hh instanceof Routeable4) return ((Routeable4) hh).accept(keys[0],keys[1],keys[2],keys[3]);
        if (hh instanceof Routeable5) return ((Routeable5) hh).accept(keys[0],keys[1],keys[2],keys[3],keys[4]);
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
    void add(String uri,Routeable4 rr) { add(new Route(uri,rr)); }
    void add(String uri,Routeable5 rr) { add(new Route(uri,rr)); }

    void make0(String uri,Factory<Routeable0> ff) { add(new Route(uri,ff)); }
    void make1(String uri,Factory<Routeable1> ff) { add(new Route(uri,ff)); }
    void make2(String uri,Factory<Routeable2> ff) { add(new Route(uri,ff)); }
    void make3(String uri,Factory<Routeable3> ff) { add(new Route(uri,ff)); }
    void make4(String uri,Factory<Routeable4> ff) { add(new Route(uri,ff)); }
    void make5(String uri,Factory<Routeable5> ff) { add(new Route(uri,ff)); }

    void make0(Route route,Factory<Routeable0> ff) { add(route.set(ff)); }
    void make1(Route route,Factory<Routeable1> ff) { add(route.set(ff)); }
    void make2(Route route,Factory<Routeable2> ff) { add(route.set(ff)); }
    void make3(Route route,Factory<Routeable3> ff) { add(route.set(ff)); }
    void make4(Route route,Factory<Routeable4> ff) { add(route.set(ff)); }
    void make5(Route route,Factory<Routeable5> ff) { add(route.set(ff)); }

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
        
        
        { if (first) make0(routes.config,self -> self::config); }
        public Object config() throws IOException, Pausable {
            File file = new File("data/config.json");
            sendFile(resp,file,false);
            return null;
        }

        { if (first) make0(new Route("POST",routes.users),self -> self::users); }
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
                dm.status.set(txn,row,MatterData.StatusEnum.away.tuple(false,0));
                System.out.println("users.insert: " + u.id + " -- " + row);
            }).await();
            if (query.getEx() != null) {
                System.out.println(query.getEx());
                query.getEx().printStackTrace();
                return "an error occurred";
            }
            return users2reps.copy(u);
        }

        { if (first) make0(routes.login,self -> self::login); }
        { if (first) make0(routes.login4,self -> self::login); }
        public Object login() throws Pausable {
            boolean v4 = uri.equals(routes.login4);
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
                setCookie(resp,matter.mmuserid,user.id,30.0,false);
                setCookie(resp,matter.mmauthtoken,user.id,30.0,true);
                resp.addField("Token",user.id);
                return users2reps.copy(user);
            }
        }
        
        { if (first) make0(routes.um,self -> self::um); }
        public Object um() throws Pausable {
            Users user = db4j.submit(txn -> {
                Integer row = dm.idmap.find(txn,uid);
                return row==null ? null : dm.users.find(txn,row);
            }).await().val;
            if (user==null)
                return setProblem(resp,HttpResponse.ST_BAD_REQUEST,"user not found");
            return users2reps.copy(user);
        }        

        { if (first) make0(routes.umt,self -> self::umt); }
        public Object umt() throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            ArrayList<Teams> teams = new ArrayList();
            db4j.submitCall(txn -> {
                Btree.Range<Btrees.II.Data> range = prefix(txn,dm.temberMap,kuser);
                while (range.next()) {
                    TeamMembers tember = dm.tembers.find(txn,range.cc.val);
                    Integer kteam = dm.idmap.find(txn,tember.teamId);
                    teams.add(dm.teams.find(txn,kteam));
                }
            }).await();
            return map(teams,team -> team2reps.copy(team),HandleNulls.skip);
        }        

        { if (first) make1(routes.txmi,self -> self::txmi); }
        public Object txmi(String teamid) throws Pausable {
            String [] userids = gson.fromJson(body(),String [].class);
            Integer kteam = get(dm.idmap,teamid);
            ArrayList<TeamMembers> tembers = new ArrayList();

            
            Spawner<TeamsMembersRep> tasker = new Spawner();
            for (String userid : userids) tasker.spawn(() -> {
                // userid -> kuser -(temberMap)-> ktembers -(filter)-> reply
                Integer kuser = get(dm.idmap,userid);
                TeamMembers tember = db4j.submit(txn ->
                        dm.filter(txn,dm.temberMap,kuser,dm.tembers,t -> t.teamId.equals(teamid))
                ).awaitb().val.val;
                return tember2reps.copy(tember);
            });
            return tasker.join();
        }

        { if (first) make1(routes.cxmi,self -> self::cxmi); }
        public Object cxmi(String chanid) throws Pausable {
            String [] userids = gson.fromJson(body(),String [].class);
            Integer kchan = get(dm.idmap,chanid);
            ArrayList<ChannelMembers> tembers = new ArrayList();

            
            Spawner<ChannelsxMembersReps> tasker = new Spawner();
            for (String userid : userids) tasker.spawn(() -> {
                // userid -> kuser -(temberMap)-> ktembers -(filter)-> reply
                Integer kuser = get(dm.idmap,userid);
                ChannelMembers cember = db4j.submit(txn ->
                        dm.filter(txn,dm.cemberMap,kuser,dm.cembers,t -> t.channelId.equals(chanid))
                ).awaitb().val.val;
                return cember2reps.copy(cember);
            });
            return tasker.join();
        }

        { if (first) make0(routes.umtm,self -> self::umtm); }
        public Object umtm() throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            ArrayList<TeamMembers> tembers = new ArrayList();
            db4j.submitCall(txn -> {
                Btree.Range<Btrees.II.Data> range = prefix(txn,dm.temberMap,kuser);
                while (range.next())
                    tembers.add(dm.tembers.find(txn,range.cc.val));
            }).await();
            return map(tembers,team -> tember2reps.copy(team),HandleNulls.skip);
        }        
        
        { if (first) make1(routes.cx,self -> self::cx); }
        public Object cx(String chanid) throws Pausable {
            Channels chan = db4j.submit(txn -> dm.get(txn,dm.channels,chanid)).await().val;
            return chan2reps.copy(chan);
        }        

        { if (first) make0(routes.invite,self -> self::invite); }
        public Object invite() throws Pausable {
            TeamsAddUserToTeamFromInviteReqs data = gson.fromJson(body(),TeamsAddUserToTeamFromInviteReqs.class);
            String query = data.inviteId;
            Integer kuser = get(dm.idmap,uid);
            if (query==null | kuser==null) throw new RuntimeException("user or team missing");
            Teams teamx = db4j.submit(txn -> {
                Btrees.IK<Teams>.Data teamcc = MatterData.filter(txn,dm.teams,tx ->
                        query.equals(tx.inviteId));
                Teams team = teamcc.val;
                Integer kteam = teamcc.key;
                if (team==null)
                    return null;
                TeamMembers tember = MatterData.filter(txn,dm.temberMap,kuser,dm.tembers,tm ->
                        team.id.equals(tm.teamId)).val;
                if (tember != null)
                    return team;
                Btrees.IK<Channels>.Data town,topic;
                town = MatterData.filter(txn,dm.chanByTeam,kteam,dm.channels,chan -> {
                    return "town-square".equals(chan.name);
                });
                topic = MatterData.filter(txn,dm.chanByTeam,kteam,dm.channels,chan -> {
                    return "off-topic".equals(chan.name);
                });
                tember = newTeamMember(team.id,uid);
                dm.addTeamMember(txn,kuser,tember);
                if (town.match)
                    dm.addChanMember(txn,kuser,town.key,newChannelMember(team.id,uid,town.val.id));
                if (topic.match)
                    dm.addChanMember(txn,kuser,topic.key,newChannelMember(team.id,uid,topic.val.id));
                return team;
            }).await().val;
            return teamx==null ? null:team2reps.copy(teamx);
        }        

        { if (first) make1(routes.cxmm,self -> self::cxmm); }
        public Object cxmm(String chanid) throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            ChannelMembers c2 = db4j.submit(txn -> {
                Btree.Range<Btrees.II.Data> range = prefix(txn,dm.cemberMap,kuser);
                while (range.next()) {
                    ChannelMembers cember = dm.cembers.find(txn,range.cc.val);
                    if (chanid.equals(cember.channelId))
                        return cember;
                }
                return null;
            }).await().val;
            return c2==null ? new int[0]:cember2reps.copy(c2);
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

        { if (first) make1(routes.teamExists,self -> self::teamExists); }
        public Object teamExists(String name) throws Pausable {
            Integer row = db4j.submit(txn -> 
                    dm.teamsByName.find(txn,name)).await().val;
            return set(new TeamsNameExistsReps(), x->x.exists=row!=null);
        }
        
        { if (first) make0(routes.unread,self -> self::unread); }
        public Object unread() throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            ArrayList<Teams> list = new ArrayList<>();
            db4j.submitCall(txn -> {
                Btree.Range<Btrees.II.Data> prefix = prefix(txn,dm.temberMap,kuser);
                while (prefix.next()) {
                    Teams team = dm.teams.find(txn,prefix.cc.val);
                    list.add(team);
                }
            }).await();
            return map(list,t -> set(new TeamsUnreadRep(), x->{ x.msgCount=0; x.mentionCount=0;}),null);
        }

        { if (first) make3(new Route("GET",routes.channelUsers),self -> self::getUsers); }
        public Object getUsers(String chanid,String page,String per) throws Pausable {
            Integer kchan = get(dm.idmap,chanid);
            ArrayList<Users> users = new ArrayList();
            db4j.submitCall(txn -> {
                Btree.Range<Btrees.II.Data> range =
                        dm.chan2cember.findPrefix(dm.chan2cember.context().set(txn).set(kchan,0));
                while (range.next()) {
                    ChannelMembers cember = dm.cembers.find(txn,range.cc.val);
                    Integer kuser = dm.idmap.find(txn,cember.userId);
                    users.add(dm.users.find(txn,kuser));
                }
            }).await();
            return map(users,users2userRep::copy,HandleNulls.skip);
        }

        { if (first) make1(routes.cxs,self -> self::cxs); }
        public Object cxs(String chanid) throws Pausable {
            Integer kchan = get(dm.idmap,chanid);
            int num = db4j.submit(txn
                    -> dm.chan2cember.findPrefix(dm.chan2cember.context().set(txn).set(kchan,0)).count()
            ).await().val;
            return set(new ChannelsxStatsReps(), x -> { x.channelId=chanid; x.memberCount=num; });
        }

        { if (first) make1(new Route("GET",routes.status),self -> self::getStatus); }
        public Object getStatus(String userid) throws Pausable {
            Integer kuser = get(dm.idmap,userid);
            MatterData.HunkTuples.Tuple tuple = db4j.submit(txn ->
                    dm.status.get(txn,kuser).yield().val
            ).await().val;
            return set(MatterData.StatusEnum.get(tuple), x -> x.userId=userid);
        }

        { if (first) make1(new Route("PUT",routes.status),self -> self::putStatus); }
        public Object putStatus(String userid) throws Pausable {
            Integer kuser = get(dm.idmap,userid);
            UsersStatusIdsRep status = gson.fromJson(body(),UsersStatusIdsRep.class);
            status.lastActivityAt = timestamp();
            db4j.submit(txn ->
                dm.status.set(txn,kuser,MatterData.StatusEnum.get(status))).await();
            return status;
        }

        { if (first) make0(routes.usi,self -> self::usi); }
        public Object usi() throws Pausable {
            String [] userids = gson.fromJson(body(),String [].class);
            Spawner<Integer> tasker = new Spawner();
            for (String userid : userids) tasker.spawn(() -> get(dm.idmap,userid));
            ArrayList<Integer> list = tasker.join();
            ArrayList<MatterData.HunkTuples.RwTuple> tuples = new ArrayList();
            
            db4j.submitCall(txn -> {
                for (int ii=0; ii < userids.length; ii++)
                    tuples.add(dm.status.get(txn,list.get(ii)));
            }).await();

            return mapi(tuples,
                    (tup,ii) -> set(MatterData.StatusEnum.get(tup.val),x -> x.userId=userids[ii]),                    
                    HandleNulls.skip);
        }

        { if (first) make2(new Route("POST",routes.postsCreate),self -> self::postsCreate); }
        public Object postsCreate(String teamid,String chanid) throws Pausable {
            TeamsxChannelsxPostsCreateReqs postReq = gson.fromJson(body(),TeamsxChannelsxPostsCreateReqs.class);
            Posts post = set(req2posts.copy(postReq),x -> {
                x.id = matter.newid();
                x.createAt = x.updateAt = timestamp();
                x.fileIds = postReq.fileIds.toArray(new String[0]);
                x.userId = uid;
            });
            // fixme - verify userid is a member of channel
            Integer kuser = get(dm.idmap,uid);
            boolean success = db4j.submit(txn -> {
                boolean match = dm.filter(txn,dm.cemberMap,kuser,dm.cembers,
                        t -> t.channelId.equals(chanid))
                        .match;
                if (match)
                    dm.addPost(txn,post);
                return match;
            }).await().val;
            if (success)
                return set(posts2rep.copy(post),x -> x.pendingPostId = postReq.pendingPostId);
            else
                return "user not a member of channel - post not created";
        }

        { if (first) make1(new Route("GET",routes.image),self -> self::image); }
        public Object image(String userid) throws Pausable, IOException {
            File file = new File("data/user.png");
            sendFile(resp,file,false);
            return null;
        }
        
        { if (first) make1(new Route("GET",routes.teams),self -> self::getTeams); }
        public Object getTeams(String teamid) throws Pausable {
            // fixme - get the page and per_page values
            ArrayList<Teams> teams = db4j.submit(txn ->
                    dm.teams.getall(txn).vals()).await().val;
            return map(teams,team2reps::copy,HandleNulls.skip);
        }        

        { if (first) make0(new Route("POST",routes.teams),self -> self::postTeams); }
        public Object postTeams() throws Pausable {
            String body = body();
            Integer kuser = get(dm.idmap,uid);
            TeamsReqs treq = gson.fromJson(body,TeamsReqs.class);
            Teams team = req2teams.copy(treq);
            team.id = matter.newid();
            team.inviteId = matter.newid();
            team.updateAt = team.createAt = new java.util.Date().getTime();
            Channels town = newChannel(team.id,"Town Square");
            Channels topic = newChannel(team.id,"Off-Topic");
            ChannelMembers townm = newChannelMember(team.id,uid,town.id);
            ChannelMembers topicm = newChannelMember(team.id,uid,topic.id);
            TeamMembers tm = newTeamMember(team.id,uid);
            tm.userId = uid;
            tm.teamId = team.id;
            tm.roles = "team_user";
            Integer result = db4j.submit(txn -> {
                Users user = dm.users.find(txn,kuser);
                team.email = user.email;
                Integer kteam = dm.addTeam(txn,team);
                if (kteam==null) return null;
                dm.addTeamMember(txn,kuser,tm);
                int ktown = dm.addChan(txn,town,kteam);
                int ktopic = dm.addChan(txn,topic,kteam);
                dm.addChanMember(txn,kuser,ktown,townm);
                dm.addChanMember(txn,kuser,ktopic,topicm);
                return kteam;
            }).await().val;
            if (result==null)
                return "team already exists";
            return team2reps.copy(team);
        }
        
        
        { if (first) make0(routes.ump,self -> () ->
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
    static boolean anyNull(Object ... objs) {
        for (Object obj : objs)
            if (obj==null) return true;
        return false;
    }

    <KK,VV> VV getb(Bmeta<?,KK,VV,?> map,KK key) {
        return db4j.submit(txn -> map.find(txn,key)).awaitb().val;
    }
    <TT> TT getb(Btrees.IK<TT> map,String key) {
        return db4j.submit(txn -> dm.get(txn,map,key)).awaitb().val;
    }
    <KK,VV> VV get(Bmeta<?,KK,VV,?> map,KK key) throws Pausable {
        return db4j.submit(txn -> map.find(txn,key)).await().val;
    }
    <TT> TT get(Btrees.IK<TT> map,String key) throws Pausable {
        return db4j.submit(txn -> dm.get(txn,map,key)).await().val;
    }
    <TT> TT get(Db4j.Utils.QueryFunction<TT> body) throws Pausable {
        return db4j.submit(body).await().val;
    }
    ArrayList<Integer> getall(Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0)).getall(cc -> cc.val);
    }
    Btree.Range<Btrees.II.Data> prefix(Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0));
    }
    
    static class Spawner<TT> {
        boolean skipNulls = true;
        ArrayList<Spawn<TT>> tasks = new ArrayList();
        Spawn<TT> spawn(kilim.Spawnable<TT> body) {
            Spawn task = Task.spawn(body);
            tasks.add(task);
            return task;
        }
        ArrayList<TT> join() throws Pausable {
            ArrayList<TT> vals = new ArrayList();
            for (Spawn<TT> task : tasks) {
                TT val = task.mb.get();
                if (val != Task.Spawn.nullValue) vals.add(val);
                else if (! skipNulls)            vals.add(null);
            }
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
    
    { add(routes.txcxpp,this::posts); }
    public Object posts(String teamid,String changid,String start,String num) throws Pausable {
        TeamsxChannelsxPostsPage060Reps posts = new TeamsxChannelsxPostsPage060Reps();
        posts.order = new ArrayList();
        posts.posts = new mm.rest.Posts();
        return posts;
    }
    
    { add(routes.umtxc,this::channels); }
    public Object channels(String teamid) throws Pausable {
        ArrayList<Channels> channels = db4j.submit(txn -> {
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
        return map(channels,chan -> chan2reps.copy(chan),HandleNulls.skip);
    }


    { add(routes.cmmv,this::cmmv); }
    public Object cmmv() throws Pausable {
        return set(new ChannelsReps.View(),x->x.status="OK");
    }

    { add(routes.license,() -> set(new LicenseClientFormatOldReps(),x->x.isLicensed="false")); }
    { add(routes.websocket,() -> "not available"); }
    
    
    
    
    enum HandleNulls {
        skip,add,map;
    }

    <SS,TT> ArrayList<TT> map(ArrayList<SS> src,Function<SS,TT> mapping,HandleNulls nulls) {
        if (nulls==null) nulls = HandleNulls.skip;
        ArrayList<TT> dst = new ArrayList<>();
        for (SS val : src) {
            if (nulls==HandleNulls.map | val != null)
                dst.add(mapping.apply(val));
            else if (val==null & nulls==HandleNulls.add)
                dst.add(null);
        }
        return dst;
    }
    <SS,TT> ArrayList<TT> mapi(ArrayList<SS> src,BiFunction<SS,Integer,TT> mapping,HandleNulls nulls) {
        if (nulls==null) nulls = HandleNulls.skip;
        ArrayList<TT> dst = new ArrayList<>();
        int ii = 0;
        for (SS val : src) {
            if (nulls==HandleNulls.map | val != null)
                dst.add(mapping.apply(val,ii));
            else if (val==null & nulls==HandleNulls.add)
                dst.add(null);
            ii++;
        }
        return dst;
    }

    public static long timestamp() { return new java.util.Date().getTime(); }
    
    static String sep = "/";
    static String qsep = "\\?";
    public static class Routes {
        String cx = "/api/v4/channels/{chanid}";
        String cxmm = "/api/v4/channels/{chanid}/members/me";
        String teamExists = "/api/v4/teams/name/{name}/exists";
        String umt = "/api/v4/users/me/teams/";
        String umtm = "/api/v4/users/me/teams/members";
        String umtxc = "/api/v4/users/me/teams/{teamid}/channels";
        String umtxcm = "/api/v4/users/me/teams/{teamid}/channels/members";
        String teams = "/api/v4/teams";
        String cmmv = "/api/v4/channels/members/me/view";
        String websocket = "/api/v3/users/websocket";
        String cxs = "/api/v4/channels/{chanid}/stats";
        String invite = "/api/v3/teams/add_user_to_team_from_invite";
        String txcxpp = "/api/v3/teams/{teamid}/channels/{chanid}/posts/page/{start}/{num}";
        String license = "/api/v4/license/client";
        String unread = "/api/v4/users/me/teams/unread";
        String status = "/api/v4/users/{userid}/status";
        String image = "/api/v3/users/{userid}/image";
        String config = "/api/v4/config/client";
        String users = "/api/v4/users";
        String channelUsers = "/api/v4/users?in_channel/page/per_page";
        String login = "/api/v3/users/login";
        String login4 = "/api/v4/users/login";
        String um = "/api/v4/users/me";
        String ump = "/api/v4/users/me/preferences";
        String umtu = "/api/v4/users/me/teams/unread";
        String txmi = "/api/v4/teams/{teamid}/members/ids";
        String usi = "/api/v4/users/status/ids";
        String cxmi = "/api/v4/channels/{chanid}/members/ids";
        String postsCreate = "/api/v3/teams/{teamid}/channels/{chanid}/posts/create";
    }
    static Routes routes = new Routes();

    public Channels newChannel(String teamId,String name) {
        Channels x = new Channels();
        x.createAt = x.updateAt = new java.util.Date().getTime();
        x.displayName = name;
        x.name = name.toLowerCase().replace(" ","-");
        x.id = matter.newid();
        x.teamId = teamId;
        return x;
    }

    public ChannelMembers newChannelMember(String teamId,String uid,String cid) {
        ChannelMembers cm = new ChannelMembers();
        cm.userId = uid;
        cm.channelId = cid;
        cm.roles = "channel_user";
        return cm;
    }
    public TeamMembers newTeamMember(String teamId,String uid) {
        TeamMembers tm = new TeamMembers();
        tm.userId = uid;
        tm.teamId = teamId;
        tm.roles = "team_user";
        return tm;
    }
    
    
    static String literal = "{desktop: \"default\", email: \"default\", mark_unread: \"all\", push: \"default\"}";
    static<TT> TT either(TT v1,TT v2) { return v1==null ? v2:v1; }
    

    static MatterData.FieldCopier<TeamsxChannelsxPostsCreateReqs,Posts> req2posts =
            new MatterData.FieldCopier(TeamsxChannelsxPostsCreateReqs.class,Posts.class);
    static MatterData.FieldCopier<Posts,Xxx> posts2rep = new MatterData.FieldCopier(Posts.class,Xxx.class);
    static MatterData.FieldCopier<Users,mm.rest.User> users2userRep =
            new MatterData.FieldCopier(Users.class,mm.rest.User.class);
    static MatterData.FieldCopier<Status,UsersStatusIdsRep> status2reps =
            new MatterData.FieldCopier(Status.class,UsersStatusIdsRep.class);
    static MatterData.FieldCopier<TeamsReqs,Teams> req2teams =
            new MatterData.FieldCopier(TeamsReqs.class,Teams.class);
    static MatterData.FieldCopier<Teams,TeamsReps> team2reps =
            new MatterData.FieldCopier(Teams.class,TeamsReps.class);
    static MatterData.FieldCopier<TeamMembers,TeamsMembersRep> tember2reps =
            new MatterData.FieldCopier(TeamMembers.class,TeamsMembersRep.class);
    static MatterData.FieldCopier<Channels,ChannelsReps> chan2reps =
            new MatterData.FieldCopier(Channels.class,ChannelsReps.class);
    static MatterData.FieldCopier<ChannelMembers,ChannelsxMembersReps> cember2reps =
            new MatterData.FieldCopier<>(ChannelMembers.class,ChannelsxMembersReps.class,(src,dst) -> {
                dst.notifyProps = MatterLess.parser.parse(either(src.notifyProps,literal));
            });
    public Object process(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        String uri = req.uriPath;
        
        Object robj = route(req,resp);
        if (robj != routeNotFound) return robj;
        
        
        if (uri.equals("/api/seth/channels")) {
            MatterLess.print(db4j.submit(txn -> dm.channels.getall(txn).vals()).await().val);
            return "channels printed";
        }
        return new int[0];
    }
    public void write(HttpResponse resp,Object obj,boolean dbg) throws IOException {
        if (obj==null) return;
        byte[] msg = null;
        if (obj instanceof String) msg = ((String) obj).getBytes();
        else if (obj instanceof byte[]) msg = (byte[]) obj;
        else msg = gson.toJson(obj).getBytes();
        if (dbg)
            System.out.println("kilim.write: " + msg);
        resp.setContentType("application/json");
        resp.getOutputStream().write(msg);
    }
    File urlToPath(HttpRequest req) {
        String base = "/home/lytles/working/fun/chernika/mattermost/webapp/dist";
        String uri = req.uriPath;
        String path = (uri!=null && uri.startsWith("/static/")) ? uri:"/root.html";
        return new File(base+path);
    }
    public void serveFile(HttpRequest req,HttpResponse resp) throws Exception, Pausable {
        File f = urlToPath(req);
        boolean headOnly = req.method.equals("HEAD");
        sendFile(resp, f, headOnly);
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
                if (req.uriPath==null || ! req.uriPath.startsWith("/api/"))
                    serveFile(req,resp);
                else
                    reply = process(req,resp);
                try {
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
        super.close();
    }

    public static void main(String[] args) throws Exception {
        JettyLooper.main(args);
    }
}
