package foobar;

import com.google.gson.JsonElement;
import static foobar.MatterControl.append;
import foobar.MatterData.Box;
import foobar.MatterData.TemberArray;
import static foobar.MatterControl.gson;
import static foobar.MatterControl.set;
import foobar.MatterData.Ibox;
import foobar.MatterData.PostInfo;
import foobar.MatterData.PrefsTypes;
import foobar.MatterData.Row;
import static foobar.MatterData.box;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kilim.Pausable;
import kilim.Task;
import kilim.Task.Spawn;
import static kilim.examples.HttpFileServer.mimeType;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.http.KeyValues;
import kilim.nio.NioSelectorScheduler.SessionFactory;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.Posts;
import mm.data.Preferences;
import mm.data.Reactions;
import mm.data.Status;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.rest.ChannelsMembersxViewReqs;
import mm.rest.ChannelsReps;
import mm.rest.ChannelsReqs;
import mm.rest.ChannelsxMembersReps;
import mm.rest.ChannelsxMembersReqs;
import mm.rest.ChannelsxStatsReps;
import mm.rest.LicenseClientFormatOldReps;
import mm.rest.NotifyUsers;
import mm.rest.PreferencesSaveReq;
import mm.rest.Reaction;
import mm.rest.TeamsAddUserToTeamFromInviteReqs;
import mm.rest.TeamsMembersRep;
import mm.rest.TeamsNameExistsReps;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.TeamsxChannelsSearchReqs;
import mm.rest.TeamsxChannelsxPostsCreateReqs;
import mm.rest.TeamsxChannelsxPostsPage060Reps;
import mm.rest.TeamsxChannelsxPostsUpdateReqs;
import mm.rest.TeamsxChannelsxPostsxDeleteRep;
import mm.rest.TeamsxMembersBatchReq;
import mm.rest.TeamsxPostsSearchReqs;
import mm.rest.TeamsxStatsReps;
import mm.rest.User;
import mm.rest.UsersAutocompleteInTeamInChannelNameSeReps;
import mm.rest.UsersLogin4Error;
import mm.rest.UsersLogin4Reqs;
import mm.rest.UsersLoginReqs;
import mm.rest.UsersReps;
import mm.rest.UsersReqs;
import mm.rest.UsersSearchReqs;
import mm.rest.UsersStatusIdsRep;
import mm.rest.Xxx;
import org.db4j.Bmeta;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Command;
import org.db4j.Db4j;
import org.db4j.Db4j.Transaction;
import org.srlutils.Simple;

public class MatterKilim {

    MatterControl matter;
    Db4j db4j;
    MatterData dm;
    MatterWebsocket ws;

    void setup(MatterControl $matter) {
        matter = $matter;
        db4j = matter.db4j;
        dm = matter.dm;
        ws = matter.ws;
    }
    
    
    public SessionFactory sessionFactory() {
        return () -> new Session();
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

    String getUserAuth(HttpRequest req) {
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
                if ((part=parse(sub,MatterControl.mmuserid+"=")) != null)
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
    

    Pattern reMention = Pattern.compile("[@#]?\\b(\\w*)");
    ArrayList<Integer> getMentions(String text) {
        ArrayList<Integer> list = new ArrayList<>();
        Matcher mat = reMention.matcher(text);
        while (mat.find()) {
            String name = mat.group(1);
            Integer kuser = matter.mentionMap.get(name);
            if (kuser != null)
                list.add(kuser);
        }
        return list;
    }

    
    static String wildcard = "{";
    static String asterisk = "*";
    public static class Route {
        String method;
        String [] parts;
        boolean varquer;
        String [] queries = new String[0];
        Routeable handler;
        String uri;
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
    
    Object route(Session session,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Route.Info info = new Route.Info(req);
        for (int ii=0; ii < route.size(); ii++) {
            Route r2 = route.get(ii);
            if (r2.test(info,req))
                return route(session,r2.handler,info.keys,req,resp);
        }
        return new Processor(session,req,resp).fallback();
    }
    Object route(Session session,Routeable hh,String [] keys,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Processor pp = new Processor(session,req,resp);
        if (hh instanceof Routeable0) return ((Routeable0) hh).accept();
        if (hh instanceof Routeable1) return ((Routeable1) hh).accept(keys[0]);
        if (hh instanceof Routeable2) return ((Routeable2) hh).accept(keys[0],keys[1]);
        if (hh instanceof Routeable3) return ((Routeable3) hh).accept(keys[0],keys[1],keys[2]);
        if (hh instanceof Routeable4) return ((Routeable4) hh).accept(keys[0],keys[1],keys[2],keys[3]);
        if (hh instanceof Routeable5) return ((Routeable5) hh).accept(keys[0],keys[1],keys[2],keys[3],keys[4]);
        if (hh instanceof Factory)
            return route(session,((Factory) hh).make(pp),keys,req,resp);
        return hh.run(keys);
    }

    /** unused but useful for debugging routing problems */
    ArrayList<Row<Route>> filterRoutes(HttpRequest req) {
        Route.Info info = new Route.Info(req);
        return filterRows(route,r -> r.test(info,req));
    }
    /**
     * filter a list, wrapping the selected items in a row, ie an index-value tuple
     * @param <TT> the type of the list
     * @param list the list
     * @param filter the filter to apply, return true to select the item
     * @return the selected indices and items
     */
    static <TT> ArrayList<Row<TT>> filterRows(java.util.Collection<TT> list,Function<TT,Boolean> filter) {
        ArrayList<Row<TT>> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(item))
                result.add(new Row(ii,item));
            ii++;
        }
        return result;
    }
    static <TT> ArrayList<TT> filter(List<TT> list,int first,int num) {
        int last = Math.min(first+num,list.size());
        return new ArrayList<>(list.subList(first,last));
    }
    static <TT> ArrayList<TT> filter(java.util.Collection<TT> list,Function<TT,Boolean> filter) {
        ArrayList<TT> result = new ArrayList<>();
        for (TT item : list) {
            if (filter.apply(item))
                result.add(item);
        }
        return result;
    }
    static <TT> ArrayList<TT> filter2(java.util.Collection<TT> list,BiFunction<Integer,TT,Boolean> filter) {
        ArrayList<TT> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(ii++,item))
                result.add(item);
        }
        return result;
    }
    static <TT> ArrayList<Integer> filterIndex(java.util.Collection<TT> list,BiFunction<Integer,TT,Boolean> filter) {
        ArrayList<Integer> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(ii++,item))
                result.add(ii);
        }
        return result;
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

    
    
    public class Processor {
        Processor() {}
        Processor(Session $session,HttpRequest $req,HttpResponse $resp) {
            session = $session;
            req = $req;
            resp = $resp;
            uri = req.uriPath;
            uid = getUserAuth(req);
        }
        Session session;
        HttpRequest req;
        HttpResponse resp;
        String uri;
        String uid;
        
        public <TT> TT body(Class<TT> klass) {
            String txt = body();
            TT val = gson.fromJson(txt,klass);
            boolean dbg = true;
            if (dbg) {
                JsonElement parsed = MatterControl.parser.parse(txt);
                String v1 = MatterControl.skipGson.toJson(val);
                String v2 = MatterControl.skipGson.toJson(parsed);
                if (! v1.equals(v2)) {
                    System.out.format("%-40s --> %s\n",uri,klass.getName());
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
        
        
        { if (first) make0(routes.config,self -> self::config); }
        public Object config() throws IOException, Pausable {
            File file = new File("data/config.json");
            session.sendFile(resp,file,false);
            return null;
        }

        { if (first) make0(new Route("POST",routes.users),self -> self::users); }
        public Object users() throws Pausable {
            UsersReqs ureq = body(UsersReqs.class);
            Users u = req2users.copy(ureq,new Users());
            u.id = matter.newid();
            u.password = matter.salt(ureq.password);
            u.updateAt = u.lastPasswordUpdate = u.createAt = timestamp();
            u.roles = "system_user";
            u.notifyProps = null; // new NotifyUsers().init(rep.username);
            u.locale = "en";
            Integer kuser = db4j.submit(txn -> dm.addUser(txn,u)).await().val;
            matter.mentionMap.put(u.username,kuser);
            ws.send.newUser(u.id);
            return users2reps.copy(u);
        }

        { if (first) make0(routes.login,self -> self::login); }
        { if (first) make0(routes.login4,self -> self::login); }
        public Object login() throws Pausable {
            boolean v4 = uri.equals(routes.login4);
            UsersLoginReqs login = v4 ? null : body(UsersLoginReqs.class);
            UsersLogin4Reqs login4 = !v4 ? null : body(UsersLogin4Reqs.class);
            String password = v4 ? login4.password : login.password;
            Users user = select(txn -> {
                Integer row;
                if (login4==null)
                    row = dm.idmap.find(txn,login.id);
                else row = dm.usersByName.find(txn,login4.loginId);
                if (row==null) {
                    matter.print(login);
                    matter.print(login4);
                }
                return row==null ? null : dm.users.find(txn,row);
            });
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
            Users user = select(txn -> {
                Integer row = dm.idmap.find(txn,uid);
                return row==null ? null : dm.users.find(txn,row);
            });
            if (user==null)
                return setProblem(resp,HttpResponse.ST_BAD_REQUEST,"user not found");
            return users2reps.copy(user);
        }        

        { if (first) make1(routes.teamsMe,self -> self::teamsMe); }
        public Object teamsMe(String teamid) throws Pausable {
            Integer kteam = get(dm.idmap,teamid);
            Teams team = select(txn -> dm.teams.find(txn,kteam));
            return team2reps.copy(team);
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
            String [] userids = body(String [].class);
            Integer kteam = get(dm.idmap,teamid);
            ArrayList<TeamMembers> tembers = new ArrayList();

            
            Spawner<TeamsMembersRep> tasker = new Spawner();
            for (String userid : userids) tasker.spawn(() -> {
                // userid -> kuser -(temberMap)-> ktembers -(filter)-> reply
                Integer kuser = get(dm.idmap,userid);
                TeamMembers tember = select(txn ->
                        dm.filter(txn,dm.temberMap,kuser,dm.tembers,t -> t.teamId.equals(teamid))
                ).val;
                return tember2reps.copy(tember);
            });
            return tasker.join();
        }

        { if (first) make1(routes.cxmi,self -> self::cxmi); }
        public Object cxmi(String chanid) throws Pausable {
            String [] userids = body(String [].class);
            Integer kchan = get(dm.idmap,chanid);
            ArrayList<ChannelMembers> tembers = new ArrayList();

            
            Spawner<ChannelsxMembersReps> tasker = new Spawner();
            for (String userid : userids) tasker.spawn(() -> {
                // userid -> kuser -(temberMap)-> ktembers -(filter)-> reply
                Integer kuser = get(dm.idmap,userid);
                ChannelMembers cember = select(txn ->
                        dm.filter(txn,dm.cemberMap,kuser,dm.cembers,t -> t.channelId.equals(chanid))
                ).val;
                return cember2reps.copy(cember);
            });
            return tasker.join();
        }

        { if (first) make1(new Route("POST",routes.cxm),self -> self::joinChannel); }
        public Object joinChannel(String chanid) throws Pausable {
            ChannelsxMembersReqs info = body(ChannelsxMembersReqs.class);
            String userid = info.userId;
            Integer kuser = get(dm.idmap,userid);
            boolean direct = info.channelId==null;
            Simple.softAssert(direct || chanid.equals(info.channelId),
                    "if these ever differ need to determine which one is correct: %s vs %s",
                    chanid, info.channelId);
            Integer kchan = get(dm.idmap,chanid);
            ChannelMembers cember = newChannelMember(userid,chanid);
            Box<Channels> chan = new Box();
            ChannelMembers result = select(txn -> {
                Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
                if (kcember != null)
                    return dm.cembers.find(txn,kcember);
                chan.val = dm.getChan(txn,kchan);
                Integer kteam = direct ? 0:dm.idmap.find(txn,chan.val.teamId);
                dm.addChanMember(txn,kuser,kchan,cember,kteam);
                return cember;
            });
            if (chan.val != null)
                ws.send.userAdded(chan.val.teamId,userid,chanid,kchan);
            return cember2reps.copy(result);
        }

        { if (first) make2(new Route("DELETE",routes.cxmx),self -> self::leaveChannel); }
        public Object leaveChannel(String chanid,String memberId) throws Pausable {
            Integer kuser = get(dm.idmap,memberId);
            Integer kchan = get(dm.idmap,chanid);
            Channels chan = select(txn -> dm.removeChanMember(txn,kuser,kchan));
            if (isOpenGroup(chan))
                ws.send.userRemoved(uid,memberId,chanid,kchan,kuser);
            else
                ws.send.userRemovedPrivate(uid,memberId,chanid,kuser);
            return set(new ChannelsReps.View(),x->x.status="OK");
        }

        { if (first) make2(new Route("DELETE",routes.txmx),self -> self::leaveTeam); }
        public Object leaveTeam(String teamid,String memberId) throws Pausable {
            Integer kuser = get(dm.idmap,memberId);
            Integer kteam = get(dm.idmap,teamid);
            db4j.submitCall(txn -> dm.removeTeamMember(txn,kuser,kteam)).await();
            ws.send.leaveTeam(memberId,teamid,kteam,kuser);
            return set(new ChannelsReps.View(),x->x.status="OK");
        }

        { if (first) make0(routes.oldTembers,self -> self::umtm); }
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
        
        { if (first) make1(new Route("DELETE",routes.cx),self -> self::deleteChannel); }
        public Object deleteChannel(String chanid) throws Pausable {
            MatterData.RemoveChanRet info = select(txn -> dm.removeChan(txn,chanid));
            ws.send.channelDeleted(chanid,info.teamid,info.kteam);
            return set(new ChannelsReps.View(),x->x.status="OK");
        }

        { if (first) make1(new Route("GET",routes.cx),self -> self::cx); }
        public Object cx(String chanid) throws Pausable {
            Channels chan = select(txn -> dm.getChan(txn,krow(txn,chanid)));
            return chan2reps.copy(chan);
        }

        { if (first) make1(new Route("POST",routes.txcSearch),self -> self::searchChannels); }
        public Object searchChannels(String teamid) throws Pausable {
            TeamsxChannelsSearchReqs body = body(TeamsxChannelsSearchReqs.class);
            ArrayList<Channels> channels = new ArrayList<>();
            Integer kteam = get(dm.idmap,teamid);
            String name = dm.fullChannelName(kteam,body.term);
            db4j.submitCall(txn -> {
                ArrayList<Integer> kchans = dm.chanByName.findPrefix(txn,name).getall(cc -> cc.val);
                for (int kchan : kchans) {
                    Channels chan = dm.getChan(txn,kchan);
                    channels.add(chan);
                }
            });
            return map(channels,chan2reps::copy,HandleNulls.skip);
        }

        { if (first) make2(new Route("GET",routes.txcName),self -> self::namedChannel); }
        public Object namedChannel(String teamid,String name) throws Pausable {
            // fixme:mmapi - only see this being used for direct channels, which aren't tied to a team ...
            Channels chan = select(txn -> {
                Integer kteam = teamid.length()==0 ? 0:dm.idmap.find(txn,teamid);
                return dm.getChanByName(txn,kteam,name).val;
            });
            return chan2reps.copy(chan);
        }
        
        { if (first) make1(routes.txmBatch,self -> self::txmBatch); }
        public Object txmBatch(String teamid) throws Pausable {
            TeamsxMembersBatchReq [] batch = body(TeamsxMembersBatchReq[].class);
            int num = batch.length;
            String [] ids = dm.filterArray(batch,String []::new,x -> x.userId);
            TemberArray tembers =
                    select(txn -> dm.addUsersToTeam(txn,null,teamid,ids));
            for (int ii=0; ii < num; ii++) {
                TeamMembers tember = tembers.get(ii);
                Integer kuser = tembers.kusers[ii];
                if (tember != null)
                    ws.send.addedToTeam(kuser,tember.teamId,tember.userId);
            }
            return map(tembers,tember -> tember2reps.copy(tember),HandleNulls.skip);
        }


        { if (first) make0(routes.invite,self -> self::invite); }
        public Object invite() throws Pausable {
            TeamsAddUserToTeamFromInviteReqs data = body(TeamsAddUserToTeamFromInviteReqs.class);
            String query = data.inviteId;
            if (query==null) throw new BadRoute(400,"user or team missing");
            Teams teamx = select(txn -> {
                Btrees.IK<Teams>.Data teamcc = MatterData.filter(txn,dm.teams,tx ->
                        query.equals(tx.inviteId));
                Teams team = teamcc.val;
                Integer kteam = teamcc.key;
                dm.addUsersToTeam(txn,kteam,team.id,uid);
                return team;
            });
            return team2reps.copy(teamx);
        }        

            
        { if (first) make1(routes.cxmm,self -> chanid -> self.getChannelMember(null,chanid,self.uid)); }
        { if (first) make3(routes.txcxmx,self -> self::getChannelMember); }
        public Object getChannelMember(String teamid,String chanid,String userid) throws Pausable {
            ChannelMembers cember = select(txn -> {
                Integer kuser = dm.idmap.find(txn,uid);
                int kchan = dm.idmap.find(txn,chanid);
                Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
                return dm.getCember(txn,kcember);
            });
            return cember2reps.copy(cember);
        }        

        { if (first) make1(routes.umtxcm,self -> self::umtxcm); }
        public Object umtxcm(String teamid) throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            ArrayList<ChannelMembers> cembers = select(txn -> {
                ArrayList<Integer> kcembers = getall(txn,dm.cemberMap,kuser);
                return dm.calcChannelUnreads(txn,kcembers,teamid);
            });
            return map(cembers,cember2reps::copy,null);
        }        

        { if (first) make1(routes.teamExists,self -> self::teamExists); }
        public Object teamExists(String name) throws Pausable {
            Integer row = select(txn -> 
                    dm.teamsByName.find(txn,name));
            return set(new TeamsNameExistsReps(), x->x.exists=row!=null);
        }
        
        { if (first) make0(routes.unread,self -> self::unread); }
        { if (first) make0(routes.umtu,self -> self::unread); }
        public Object unread() throws Pausable {
            return select(txn -> dm.calcUnread(txn,uid));
        }

        { if (first) make3(new Route("GET",routes.channelUsers),self -> self::chanUsers); }
        public Object chanUsers(String chanid,String page,String per) throws Pausable {
            Integer kchan = get(dm.idmap,chanid);
            ArrayList<Users> users = new ArrayList();
            db4j.submitCall(txn -> {
                Btree.Range<Tuplator.III.Data> range =
                        dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true));
                while (range.next()) {
                    ChannelMembers cember = dm.cembers.find(txn,range.cc.val);
                    Integer kuser = dm.idmap.find(txn,cember.userId);
                    Users user = dm.users.find(txn,kuser);
                    users.add(user);
                }
            }).await();
            return map(users,users2userRep::copy,HandleNulls.skip);
        }

        { if (first) make2(new Route("GET",routes.allUsers),self -> self::getUsers); }
        public Object getUsers(String page,String per) throws Pausable {
            int kpage = Integer.parseInt(page);
            int num = Integer.parseInt(per);
            int m1=kpage*num, m2=m1+num;
            ArrayList<Users> users = new ArrayList();
            db4j.submitCall(txn -> {
                ArrayList<Integer> kusers = dm.usersByName.getall(txn).getall(cc -> cc.val);
                for (int ii=m1,jj=0; ii < m2 & jj < kusers.size(); ii++,jj++) {
                    int kuser = kusers.get(ii);
                    Users user = dm.users.find(txn,kuser);
                    users.add(user);
                }
            }).await();
            return map(users,users2userRep::copy,HandleNulls.skip);
        }
        { if (first) make0(new Route("POST",routes.search),self -> self::search); }
        public Object search() throws Pausable, Exception {
            UsersSearchReqs body = body(UsersSearchReqs.class);
            ArrayList<Users> users = select(txn -> {
                ArrayList<Integer> kusers = dm.usersByName.findPrefix(txn,body.term).getall(cc -> cc.val);

                Integer kteam    = dm.getk(txn,body.teamId);
                Integer kteamNot = dm.getk(txn,body.notInTeamId);
                Integer kchanNot = dm.getk(txn,body.notInChannelId);
                ArrayList<Integer> team = dm.getKuser(txn,dm.team2tember,kteam);
                ArrayList<Integer> notTeam = dm.getKuser(txn,dm.team2tember,kteamNot);
                ArrayList<Integer> notChan = dm.getKuser(txn,dm.chan2cember,kchanNot);
                kusers = Tuplator.join(kusers,team);
                kusers = Tuplator.not(kusers,notTeam);
                kusers = Tuplator.not(kusers,notChan);
                return dm.get(txn,dm.users,kusers);
            });
            return map(users,users2userRep::copy,HandleNulls.skip);
        }


        { if (first) make3(new Route("GET",routes.autoUser),self -> self::autocompleteUsers); }
        public Object autocompleteUsers(String teamid,String chanid,String name) throws Pausable {
            // byName                           -> kuser1
            // team2tember -> kuser,kteam       -> kuser2
            // chan2cember -> kuser,kteam,kchan -> kuser3
            // merge (if chan or team) -> kuser
            
            int kteam = teamid==null || teamid.length()==0 ? -1:get(dm.idmap,teamid);
            int kchan = chanid==null || chanid.length()==0 ? -1:get(dm.idmap,chanid);
            ArrayList<Users> users = select(txn -> {
                ArrayList<Integer> kusers = dm.usersByName.findPrefix(txn,name).getall(cc -> cc.val);

                ArrayList<Integer> kusers2 = null;
                if      (kchan >= 0) kusers2 = dm.getKuser(txn,dm.chan2cember,kchan);
                else if (kteam >= 0) kusers2 = dm.getKuser(txn,dm.team2tember,kteam);
                if (kusers2 != null)
                    kusers = Tuplator.join(kusers,kusers2);

                return dm.get(txn,dm.users,kusers);
            });
            ArrayList<User> map = map(users,users2userRep::copy,HandleNulls.skip);
            return set(new UsersAutocompleteInTeamInChannelNameSeReps(),x -> x.users=map);
        }

        { if (first) make3(new Route("GET",routes.teamUsers),self -> self::getTeamUsers); }
        public Object getTeamUsers(String teamid,String page,String per) throws Pausable {
            Integer kteam = get(dm.idmap,teamid);
            String chanid = req.getQueryComponents().get("not_in_channel");
            boolean nochan = chanid.isEmpty();
            Integer kchan = nochan ? null:get(dm.idmap,chanid);
            ArrayList<Users> users = new ArrayList();
            db4j.submitCall(txn -> {
                Btree.Range<Tuplator.III.Data> teamz =
                        dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true));
                ArrayList<Integer> kusers = nochan ? null:
                        dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true)).getall(cc -> cc.key.v2);
                HashSet<Integer> excluded = nochan ? null:new HashSet<>(kusers);
                while (teamz.next()) {
                    int kuser = teamz.cc.key.v2, ktember = teamz.cc.val;
                    if (!nochan && excluded.contains(kuser)) continue;
                    users.add(dm.users.find(txn,kuser));
                }
            }).await();
            return map(users,users2userRep::copy,HandleNulls.skip);
        }

        { if (first) make3(new Route("GET",routes.nonTeamUsers),self -> self::nonTeamUsers); }
        public Object nonTeamUsers(String teamid,String page,String per) throws Pausable {
            Integer kteam = get(dm.idmap,teamid);
            ArrayList<Users> users = new ArrayList();
            db4j.submitCall(txn -> {
                Btree.Range<Tuplator.III.Data> teamz =
                        dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true));
                ArrayList<Integer> teamUsers = teamz.getall(cc -> cc.key.v2);
                HashSet<Integer> map = new HashSet<>(teamUsers);
                Btrees.IK<Users>.Range range = dm.users.getall(txn);
                while (range.next())
                    if (! map.contains(range.cc.key))
                        users.add(range.cc.val);
            }).await();
            return map(users,users2userRep::copy,HandleNulls.skip);
        }

        { if (first) make0(new Route("POST",routes.usersIds),self -> self::getUsersByIds); }
        public Object getUsersByIds() throws Pausable {
            String [] userids = body(String [].class);
            ArrayList<Users> users = new ArrayList();
            db4j.submitCall(txn -> {
                for (String userid : userids)
                    users.add(dm.get(txn,dm.users,userid));
            }).await();
            return map(users,users2userRep::copy,HandleNulls.skip);
        }

        { if (first) make1(routes.cxs,self -> self::cxs); }
        public Object cxs(String chanid) throws Pausable {
            Integer kchan = get(dm.idmap,chanid);
            int num = select(txn ->
                    dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true)).count());
            return set(new ChannelsxStatsReps(), x -> { x.channelId=chanid; x.memberCount=num; });
        }

        { if (first) make1(routes.txs,self -> self::txs); }
        public Object txs(String teamid) throws Pausable {
            Integer kteam = get(dm.idmap,teamid);
            int num = select(txn ->
                    dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true)).count());
            // fixme - where should active member count come from ?
            //   maybe from the websocket active connections ???
            return set(new TeamsxStatsReps(),
                    x -> { x.teamId=teamid; x.activeMemberCount=x.totalMemberCount=num; });
        }

        { if (first) make1(new Route("GET",routes.status),self -> self::getStatus); }
        public Object getStatus(String userid) throws Pausable {
            Integer kuser = get(dm.idmap,userid);
            Tuplator.HunkTuples.Tuple tuple = select(txn ->
                    dm.status.get(txn,kuser).yield().val);
            return set(Tuplator.StatusEnum.get(tuple), x -> x.userId=userid);
        }

        { if (first) make1(new Route("PUT",routes.status),self -> self::putStatus); }
        public Object putStatus(String userid) throws Pausable {
            // fixme - need to handle status on timeout and restart
            // based on sniffing ws frames, mattermost uses a 6 minute timer at which point you're marked "away"
            Integer kuser = get(dm.idmap,userid);
            UsersStatusIdsRep status = body(UsersStatusIdsRep.class);
            status.lastActivityAt = timestamp();
            select(txn ->
                dm.status.set(txn,kuser,Tuplator.StatusEnum.get(status)));
            return status;
        }

        { if (first) make0(routes.usi,self -> self::usi); }
        public Object usi() throws Pausable {
            String [] userids = body(String [].class);
            Spawner<Integer> tasker = new Spawner(false);
            for (String userid : userids) tasker.spawn(() -> get(dm.idmap,userid));
            ArrayList<Integer> list = tasker.join();
            ArrayList<Tuplator.HunkTuples.RwTuple> tuples = new ArrayList();
            
            db4j.submitCall(txn -> {
                for (int ii=0; ii < userids.length; ii++) {
                    Integer kuser = list.get(ii);
                    tuples.add(dm.status.get(txn,kuser));
                }
            }).await();

            return mapi(tuples,
                    (tup,ii) -> set(Tuplator.StatusEnum.get(tup.val),x -> x.userId=userids[ii]),                    
                    HandleNulls.skip);
        }

        { if (first) make4(new Route("GET",routes.getPosts),self -> self::getPosts); }
        public Object getPosts(String teamid,String chanid,String firstTxt,String numTxt) throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            Integer kchan = get(dm.idmap,chanid);
            int first = Integer.parseInt(firstTxt);
            int num = Integer.parseInt(numTxt);
            ArrayList<Row<Posts>> posts = new ArrayList();
            db4j.submitCall(txn -> {
                Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan,true));
                for (int ii=0; ii < first && range.goprev(); ii++) {}
                for (int ii=0; ii < num && range.prev(); ii++)
                    posts.add(new Row<>(range.cc.key.v2,range.cc.val));
                dm.getPostsInfo(txn,posts);
            }).await();
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            for (Row<Posts> row : posts) {
                Posts post = row.val;
                rep.order.add(post.id);
                rep.posts.put(post.id,posts2rep.copy(post));
            }
            return rep;
        }
        { if (first) make5(new Route("GET",routes.postsAfter),self -> self::getPostsAfter); }
        public Object getPostsAfter(String teamid,String chanid,String postid,String firstTxt,String numTxt) throws Pausable {
            int first = Integer.parseInt(firstTxt);
            int num = Integer.parseInt(numTxt);
            ArrayList<Row<Posts>> posts = new ArrayList();
            db4j.submitCall(txn -> {
                Integer kuser = dm.idmap.find(txn,uid);
                Integer kchan = dm.idmap.find(txn,chanid);
                Integer kpost = dm.idmap.find(txn,postid);
                Tuplator.IIK<Posts>.Range range = dm.channelPosts.findRange(txn,
                        new Tuplator.Pair(kchan,kpost),new Tuplator.Pair(kchan+1,0));
                for (int ii=0; ii < first && range.gonext(); ii++) {}
                for (int ii=0; ii < num && range.next(); ii++)
                    posts.add(new Row<>(range.cc.key.v2,range.cc.val));
                dm.getPostsInfo(txn,posts);
            }).await();
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            for (Row<Posts> row : posts) {
                Posts post = row.val;
                rep.order.add(post.id);
                rep.posts.put(post.id,posts2rep.copy(post));
            }
            return rep;
        }
        { if (first) make5(new Route("GET",routes.postsBefore),self -> self::getPostsBefore); }
        public Object getPostsBefore(String teamid,String chanid,String postid,String firstTxt,String numTxt) throws Pausable {
            int first = Integer.parseInt(firstTxt);
            int num = Integer.parseInt(numTxt);
            ArrayList<Row<Posts>> posts = new ArrayList();
            db4j.submitCall(txn -> {
                Integer kuser = dm.idmap.find(txn,uid);
                Integer kchan = dm.idmap.find(txn,chanid);
                Integer kpost = dm.idmap.find(txn,postid);
                Tuplator.IIK<Posts>.Range range = dm.channelPosts.findRange(txn,
                        new Tuplator.Pair(kchan,0),new Tuplator.Pair(kchan,kpost));
                for (int ii=0; ii < first && range.goprev(); ii++) {}
                for (int ii=0; ii < num && range.prev(); ii++)
                    posts.add(new Row<>(range.cc.key.v2,range.cc.val));
                dm.getPostsInfo(txn,posts);
            }).await();
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            for (Row<Posts> row : posts) {
                Posts post = row.val;
                rep.order.add(post.id);
                rep.posts.put(post.id,posts2rep.copy(post));
            }
            return rep;
        }

        { if (first) make2(new Route("POST",routes.updatePost),self -> self::updatePost); }
        public Object updatePost(String teamid,String chanid) throws Pausable {
            TeamsxChannelsxPostsUpdateReqs update = body(TeamsxChannelsxPostsUpdateReqs.class);
            Integer kpost = get(dm.idmap,update.id);
            Integer kchan = get(dm.idmap,update.channelId);
            if (! chanid.equals(update.channelId))
                System.out.format("matter:updatePost - unexpected mismatch between body(%s) and params(%s)\n",
                        update.channelId,chanid);
            Posts post = select(txn -> {
                PostInfo info = dm.getPostInfo(txn,kpost);
                Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan,kpost));
                range.next();
                Posts prev = range.cc.val;
                prev.message = update.message;
                prev.editAt = prev.updateAt = timestamp();
                // fixme - bmeta.update should have a nicer api, ie bmeta.update(txn,key,val)
                // fixme - bmeta.remove should have a nicer api, ie bmeta.remove(txn,key)
                range.update();
                info.finish(txn,prev,true);
                dm.postfo.update.set(txn,kpost,prev.updateAt);
                return prev;
            });
            Xxx reply = posts2rep.copy(post);
            ws.send.postEdited(reply,update.channelId,kchan);
            return reply;
        }

        { if (first) make3(new Route("POST",routes.deletePost),self -> self::deletePost); }
        public Object deletePost(String teamid,String chanid,String postid) throws Pausable {
            long time = timestamp();
            Ibox kchan = new Ibox();
            Posts post = select(txn -> {
                kchan.val = dm.idmap.find(txn,chanid);
                Integer kpost = dm.idmap.find(txn,postid);
                dm.postfo.delete.set(txn,kpost,time);
                return dm.getPostInfo(txn,kchan.val,kpost);
            });
            Xxx reply = posts2rep.copy(post);
            ws.send.postDeleted(reply,chanid,kchan.val);
            return set(new TeamsxChannelsxPostsxDeleteRep(),x -> x.id=post.id);
        }
        
        { if (first) make1(new Route("POST",routes.searchPosts),self -> self::searchPosts); }
        public Object searchPosts(String teamid) throws Pausable {
            TeamsxPostsSearchReqs search = body(TeamsxPostsSearchReqs.class);
            // fixme - handle teamid and the various search options, eg exact and not_in_chan
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            select(txn -> {
                ArrayList<Integer> kposts = dm.postsIndex.search(txn,search.terms);
                if (kposts.isEmpty()) return null;
                ArrayList<Command.RwInt> kchans = dm.get(txn,dm.postfo.kchan,kposts);
                ArrayList<Command.RwInt> kteams = dm.get(txn,dm.postfo.kteam,kposts);
                txn.submitYield();
                for (int ii=0; ii < kposts.size(); ii++) {
                    Posts post = dm.getPostInfo(txn,kchans.get(ii).val,kposts.get(ii));
                    rep.order.add(post.id);
                    rep.posts.put(post.id,posts2rep.copy(post));
                }
                return null;
            });
            return rep;
        }
        
        { if (first) make2(new Route("GET",routes.permalink),self -> self::permalink); }
        public Object permalink(String teamid,String postid) throws Pausable {
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            rep.order.add(postid);
            ArrayList<Posts> posts = new ArrayList<>();
            db4j.submitCall(txn -> {
                Integer kpost = dm.idmap.find(txn,postid);
                int kchan = dm.postfo.kchan.get(txn,kpost).yield().val;
                Posts post = dm.getPostInfo(txn,kchan,kpost);
                posts.add(post);
                if (post.rootId != null) {
                    int kroot = dm.idmap.find(txn,post.rootId);
                    ArrayList<Integer> kposts = dm.root2posts.findPrefix(
                            dm.root2posts.context().set(txn).set(kroot,0)
                    ).getall(cc -> cc.val);
                    kposts.add(kroot);
                    for (int ii=0; ii < kposts.size(); ii++) {
                        int k2 = kposts.get(ii);
                        if (k2==kpost) continue;
                        Posts post2 = dm.getPostInfo(txn,kchan,k2);
                        posts.add(post2);
                    }
                }
            }).await();
            for (Posts post : posts) rep.posts.put(post.id,posts2rep.copy(post));
            return rep;
        }
        { if (first) make3(new Route("GET",routes.getFlagged),self -> self::getFlagged); }
        public Object getFlagged(String teamid,String firstTxt,String numTxt) throws Pausable {
            int first = Integer.parseInt(firstTxt);
            int num = Integer.parseInt(numTxt);
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            call(txn -> {
                Integer kuser = dm.idmap.find(txn,uid);
                Integer kteam = dm.idmap.find(txn,teamid);
                ArrayList<Integer> tmp = new ArrayList(), kposts = tmp;
                dm.prefs.findPrefix(txn,new Tuplator.Pair(kuser,true)).visit(cc -> {
                    if (PrefsTypes.flagged_post.test(cc.val,"true"))
                        tmp.add(cc.key.v2);
                });
                ArrayList<Command.RwInt> kteams = MatterData.get(txn,dm.postfo.kteam,kposts);
                txn.submitYield();
                kposts = filter2(kposts,(ii,row) -> either(kteams.get(ii).val,0,kteam));
                kposts = filter(kposts,first,num);
                ArrayList<Command.RwInt> kchans = MatterData.get(txn,dm.postfo.kchan,kposts);
                txn.submitYield();
                for (int ii=0; ii < kposts.size(); ii++) {
                    Posts post = dm.getPostInfo(txn,kchans.get(ii).val,kposts.get(ii));
                    rep.order.add(post.id);
                    rep.posts.put(post.id,posts2rep.copy(post));
                }
            });
            return rep;
        }
        { if (first) make2(new Route("GET",routes.getPinned),self -> self::pinnedPosts); }
        public Object pinnedPosts(String teamid,String chanid) throws Pausable {
            TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
            call(txn -> {
                Integer kchan = dm.idmap.find(txn,chanid);
                ArrayList<Integer> kposts = dm.pins.findPrefix(txn,new Tuplator.Pair(kchan,true)).getall(cc -> cc.key.v2);
                for (int ii=0; ii < kposts.size(); ii++) {
                    Posts post = dm.getPostInfo(txn,kchan,kposts.get(ii));
                    rep.order.add(post.id);
                    rep.posts.put(post.id,posts2rep.copy(post));
                }
            });
            return rep;
        }
        { if (first) make3(new Route("POST",routes.unpinPost),self -> self::pinPost); }
        { if (first) make3(new Route("POST",routes.pinPost),self -> self::pinPost); }
        public Object pinPost(String teamid,String chanid,String postid) throws Pausable {
            boolean pin = req.uriPath.endsWith("/pin");
            Ibox kchan = new Ibox();
            // fixme:dry - this and updatePost share code
            Posts post = select(txn -> {
                kchan.val = dm.idmap.find(txn,chanid);
                Integer kpost = dm.idmap.find(txn,postid);
                PostInfo info = dm.getPostInfo(txn,kpost);
                Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan.val,kpost));
                range.next();
                Posts prev = range.cc.val;
                info.finish(txn,prev,true);
                if (prev.isPinned==pin)
                    return prev;
                prev.isPinned = pin;
                prev.updateAt = timestamp();
                range.update();
                dm.postfo.update.set(txn,kpost,prev.updateAt);
                if (pin)
                    dm.pins.insert(txn,new Tuplator.Pair(kchan.val,kpost),null);
                else
                    dm.pins.remove(txn,new Tuplator.Pair(kchan.val,kpost));
                return prev;
            });
            Xxx reply = posts2rep.copy(post);
            ws.send.postEdited(reply,chanid,kchan.val);
            return reply;
        }

        { if (first) make3(new Route("GET",routes.reactions),self -> self::getReactions); }
        public Object getReactions(String teamid,String chanid,String postid) throws Pausable {
            ArrayList<Reactions> reactions = select(txn -> {
                Integer kpost = dm.idmap.find(txn,postid);
                Tuplator.Pair key = new Tuplator.Pair(kpost,true);
                return dm.reactions.findPrefix(txn,key).getall(cc -> cc.val);
            });
            return map(reactions,reactions2rep::copy,null);
        }

        { if (first) make3(new Route("POST",routes.deleteReaction),self -> self::saveReaction); }
        { if (first) make3(new Route("POST",routes.saveReaction),self -> self::saveReaction); }
        public Object saveReaction(String teamid,String chanid,String postid) throws Pausable {
            boolean save = req.uriPath.endsWith("/save");
            Reaction body = body(Reaction.class);
            body.createAt = timestamp();
            Reactions reaction = req2reactions.copy(body);
            Ibox kchan = new Ibox();
            Posts post = select(txn -> {
                Integer kpost = dm.idmap.find(txn,reaction.postId);
                Command.RwInt chanCmd = dm.postfo.kchan.get(txn,kpost);
                Command.RwInt numReact = dm.postfo.numReactions.get(txn,kpost);
                Integer kuser = dm.idmap.find(txn,reaction.userId);
                kchan.val = chanCmd.val;
                Tuplator.Pair key = new Tuplator.Pair(kpost,kuser);
                Tuplator.IIK<Reactions>.Range range =
                        dm.reactions.findPrefix(txn,key);
                while (range.next())
                    if (range.cc.val.emojiName.equals(reaction.emojiName))
                        break;
                if (save==range.cc.match) return null;
                range.cc.set(key,reaction);
                if (save) range.insert();
                else range.remove();
                dm.postfo.update.set(txn,kpost,timestamp());
                dm.postfo.numReactions.set(txn,kpost,numReact.val+(save?1:-1));
                return dm.getPostInfo(txn,kchan.val,kpost);
            });
            Xxx reply = posts2rep.copy(post);
            if (post != null) {
                ws.send.reactionAdded(body,save,chanid,kchan.val);
                ws.send.postEdited(reply,chanid,kchan.val);
            }
            return body;
        }
            
        { if (first) make2(new Route("POST",routes.createPosts),self -> self::createPosts); }
        public Object createPosts(String teamid,String chanid) throws Pausable {
            TeamsxChannelsxPostsCreateReqs postReq = body(TeamsxChannelsxPostsCreateReqs.class);
            Posts post = newPost(req2posts.copy(postReq),uid,postReq.fileIds.toArray(new String[0]));
            // fixme - handle fileIds
            // fixme - verify userid is a member of channel
            // fixme - use the array overlay to finf this faster

            ArrayList<Integer> kmentions = getMentions(post.message);
            ArrayList<String> mentionIds = new ArrayList<>();
            Ibox kchan = new Ibox();
            Box<Users> user = box();
            Box<Channels> chan = box();
            boolean success = select(txn -> {
                Integer kuser = dm.idmap.find(txn,uid);
                kchan.val = dm.idmap.find(txn,chanid);
                Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan.val,kuser));
                if (kcember==null)
                    return false;
                Channels c2 = chan.val = dm.getChan(txn,kchan.val);
                if (isDirect(c2)) {
                    // hidden dependency - kcembers should be consecutive and in sort order
                    int kcember2 = c2.name.startsWith(uid) ? kcember+1:kcember-1;
                    int kmention = dm.links.kuser.get(txn,kcember2).yield().val;
                    kmentions.add(kmention);
                }
                for (Integer kmention : kmentions)
                    mentionIds.add(dm.users.find(txn,kmention).id);
                dm.addPost(txn,kchan.val,post,kmentions);
                user.val = dm.users.find(txn,kuser);
                return true;
            });
            if (! success)
                return "user not a member of channel - post not created";
            Xxx reply = set(posts2rep.copy(post),x -> x.pendingPostId = postReq.pendingPostId);
            // fixme - mentions need to be sent to websocket
            ws.send.posted(reply,chan.val,user.val.username,kchan.val,mentionIds);
            return reply;
        }

        { if (first) make1(new Route("GET",routes.image),self -> self::image); }
        public Object image(String userid) throws Pausable, IOException {
            File file = new File("data/user.png");
            session.sendFile(resp,file,false);
            return null;
        }
        
        { if (first) make0(new Route("POST",routes.savePreferences),self -> self::savePref); }
        public Object savePref() throws Pausable {
            return putPref(null);
        }        

        { if (first) make0(new Route("POST",routes.deletePrefs),self -> self::delPref); }
        public Object delPref() throws Pausable {
            PreferencesSaveReq [] body = body(PreferencesSaveReq [].class);
            ArrayList<Preferences> prefs = map(java.util.Arrays.asList(body),req2prefs::copy,null);
            int num = body.length;
            db4j.submitCall(txn -> {
                for (int ii=0; ii < num; ii++) {
                    Preferences pref = prefs.get(ii);
                    Integer kuser = dm.idmap.find(txn,pref.userId);
                    Integer krow = dm.idmap.find(txn,pref.name);
                    dm.prefs.findPrefix(txn,new Tuplator.Pair(kuser,krow))
                            .first(cc -> pref.category.equals(cc.val.category))
                            .remove();
                }
            }).await();
            return set(new ChannelsReps.View(),x->x.status="OK");
        }        

        { if (first) make1(new Route("PUT",routes.uxPreferences),self -> self::putPref); }
        public Object putPref(String userid) throws Pausable {
            PreferencesSaveReq [] body = body(PreferencesSaveReq [].class);
            ArrayList<Preferences> prefs = map(java.util.Arrays.asList(body),req2prefs::copy,null);
            int num = body.length;
            int [] kusers = new int[num];
            db4j.submitCall(txn -> {
                for (int ii=0; ii < num; ii++) {
                    if (userid==null | ii==0)
                        kusers[ii] = dm.idmap.find(txn,body[ii].userId);
                    else {
                        kusers[ii] = kusers[0];
                        if (userid != null && !userid.equals(body[ii].userId))
                            // fixme - this check is presumably redundant but if they do disagree it's not
                            //   clear which id should be honored, ie what's the point of the userid param ???
                            System.out.format(
                                    "matter.warning: preferences userid mismatch ... %s, %s\n",
                                    userid,body[ii].userId);
                    }
                    Preferences pref = prefs.get(ii);
                    Integer krow = dm.idmap.find(txn,pref.name);
                    dm.prefs.findPrefix(txn,new Tuplator.Pair(kusers[ii],krow))
                            .first(cc -> pref.category.equals(cc.val.category))
                            .set(cc -> cc.val=pref)
                            .upsert();
                }
            }).await();
            for (int ii=0; ii < num; ii++)
                ws.send.preferencesChanged(kusers[ii],body[ii].userId,body[ii]);
            return true;
        }        
        { if (first) make0(new Route("GET",routes.umPreferences),self -> self::getPref); }
        public Object getPref() throws Pausable {
            ArrayList<Preferences> prefs = select(txn -> {
                int kuser = dm.idmap.find(txn,uid);
                return dm.prefs.findPrefix(txn,new Tuplator.Pair(kuser,true)).getall(cc -> cc.val);
            });
            return map(prefs,prefs2rep::copy,HandleNulls.skip);
        }

        { if (first) make2(new Route("GET",routes.getTeams),self -> self::getTeams); }
        public Object getTeams(String page,String perPage) throws Pausable {
            // fixme - get the page and per_page values
            ArrayList<Teams> teams = select(txn -> dm.teams.getall(txn).vals());
            return map(teams,team2reps::copy,HandleNulls.skip);
        }        

        { if (first) make1(new Route("GET",routes.umtxc),self -> self::myTeamChannels); }
        public Object myTeamChannels(String teamid) throws Pausable {
            Integer kuser = get(dm.idmap,uid);
            Integer kteamDesired = get(dm.idmap,teamid);
            if (kteamDesired==null)
                return null;
            ArrayList<Channels> channels = new ArrayList();
            db4j.submitCall(txn -> {
                ArrayList<Integer> kcembers
                        = dm.cemberMap.findPrefix(dm.cemberMap.context().set(txn).set(kuser,0)).
                                getall(cc -> cc.val);

                int num = kcembers.size();
                MatterData.ChannelGetter [] getters = new MatterData.ChannelGetter[num];
                for (int ii=0; ii < num; ii++)
                    getters[ii] = dm.new ChannelGetter(txn).prep(kcembers.get(ii));
                txn.submitYield();
                for (int ii = 0; ii < num; ii++)
                    getters[ii].first(null);
                txn.submitYield();
                for (int ii = 0; ii < num; ii++)
                    channels.add(getters[ii].get(kteamDesired,1));
            }).await();
            return map(channels,chan -> chan2reps.copy(chan),HandleNulls.skip);
        }

        { if (first) make1(new Route("POST",routes.upload),self -> self::upload); }
        public Object upload(String teamid) throws Pausable, Exception {
            throw new BadRoute(501,"images are disabled - code has been stashed");
        }

        { if (first) make0(new Route("PUT",routes.patch),self -> self::patch); }
        public Object patch() throws Pausable, Exception {
            NotifyUsers body = body(NotifyUsers.class);
            Integer kuser = get(dm.idmap,uid);
            throw new BadRoute(403,"feature is not implemented");
        }

        { if (first) make0(new Route("POST",routes.cmmv),self -> self::cmmv); }
        public Object cmmv() throws Pausable {
            ChannelsMembersxViewReqs body = body(ChannelsMembersxViewReqs.class);
            // fixme - need to figure out what this is supposed to do (other than just reply with ok)
            boolean update = body.channelId.length() > 0;
            // fixme::immediacy - on rollback, could update using a newer count
            if (update) db4j.submitCall(txn -> {
                Integer kchan = dm.idmap.find(txn,body.channelId);
                Integer kuser = dm.idmap.find(txn,uid);
                Command.RwInt count = dm.chanfo.msgCount.get(txn,kchan);
                Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
                dm.links.msgCount.set(txn,kcember,count.val);
                dm.links.mentionCount.set(txn,kcember,0);
            });
            return set(new ChannelsReps.View(),x->x.status="OK");
        }

        { if (first) make0(new Route("POST",routes.channels),self -> self::postChannel); }
        public Object postChannel() throws Pausable {
            ChannelsReqs body = body(ChannelsReqs.class);
            Channels chan = req2channel.copy(body);
            ChannelMembers cember = newChannelMember(uid,chan.id);
            Integer kuser = get(dm.idmap,uid);
            Integer kteam = get(dm.idmap,chan.teamId);
            db4j.submitCall(txn -> {
                int kchan = dm.addChan(txn,chan,kteam);
                dm.addChanMember(txn,kuser,kchan,cember,kteam);
            }).await();
            return chan2reps.copy(chan);
        }

        { if (first) make0(new Route("POST",routes.direct),self -> () -> self.createGroup(false)); }
        { if (first) make1(new Route("POST",routes.createGroup),self -> teamid -> self.createGroup(true)); }
        ChannelsReps createGroup(boolean group) throws Pausable {
            // for a direct message, the format is: [initiator, teammate]
            String [] body = body(String [].class);
            String [] userids = group ? append(body,uid) : body;
            int num = userids.length;
            String [] names = new String[num];
            java.util.Arrays.sort(userids);
            Channels chan = group
                    ? newChannel("",MatterControl.sha1hex(userids),"","G")
                    : newChannel("",userids[0] + "__" + userids[1],"","D");
            ChannelMembers [] cembers = new ChannelMembers[num];
            for (int ii=0; ii < num; ii++)
                cembers[ii] = newChannelMember(userids[ii],chan.id);
            Row<Channels> row = select(txn -> {
                Integer kteam = 0;
                Row<Channels> existing = dm.getChanByName(txn,kteam,chan.name);
                if (existing != null)
                    return existing;
                if (group) {
                    for (int ii=0; ii < num; ii++)
                        names[ii] = dm.get(txn,dm.users,userids[ii]).username;
                    java.util.Arrays.sort(names);
                    chan.displayName = String.join(", ", names);
                }
                int kchan = dm.addChan(txn,chan,kteam);
                // hidden dependency - for direct channels, kcmebers must be consecutive
                for (int ii=0; ii < num; ii++)
                    dm.addChanMember(txn,null,kchan,cembers[ii],kteam);
                return new Row(kchan,chan);
            });
            if (group)
                ws.send.groupAdded(userids,row.val.id,row.key);
            else {
                String teammate = body[1];
                ws.send.directAdded(teammate,row.val.id,row.key);
            }
            return chan2reps.copy(row.val);
        }

        { if (first) make0(new Route("POST",routes.teams),self -> self::postTeams); }
        public Object postTeams() throws Pausable {
            String body = Processor.this.body();
            Integer kuser = get(dm.idmap,uid);
            TeamsReqs treq = gson.fromJson(body,TeamsReqs.class);
            Teams team = req2teams.copy(treq);
            team.id = matter.newid();
            team.inviteId = matter.newid();
            team.updateAt = team.createAt = new java.util.Date().getTime();
            Channels town = newChannel(team.id,TOWN[0],TOWN[1],"O");
            Channels topic = newChannel(team.id,TOPIC[0],TOPIC[1],"O");
            ChannelMembers townm = newChannelMember(uid,town.id);
            ChannelMembers topicm = newChannelMember(uid,topic.id);
            TeamMembers tm = newTeamMember(team.id,uid);
            tm.userId = uid;
            tm.teamId = team.id;
            tm.roles = "team_user";
            Posts townp = newPost(newPost("user has joined the channel",town.id),uid,null);
            Posts topicp = newPost(newPost("user has joined the channel",topic.id),uid,null);
            townp.type = MatterData.PostsTypes.system_join_channel.name();
            topicp.type = MatterData.PostsTypes.system_join_channel.name();
            Integer result = select(txn -> {
                Users user = dm.users.find(txn,kuser);
                team.email = user.email;
                Integer kteam = dm.addTeam(txn,team);
                if (kteam==null) return null;
                dm.addTeamMember(txn,kuser,kteam,tm);
                int ktown = dm.addChan(txn,town,kteam);
                int ktopic = dm.addChan(txn,topic,kteam);
                dm.addChanMember(txn,kuser,ktown,townm,kteam);
                dm.addChanMember(txn,kuser,ktopic,topicm,kteam);
                dm.addPost(txn,ktown,townp,null);
                dm.addPost(txn,ktopic,topicp,null);
                return kteam;
            });
            if (result==null)
                return "team already exists";
            return team2reps.copy(team);
        }
        
        

        { if (first) make0("/api/v3/general/log_client",self -> () -> new int[0]); }
        
        Object fallback() {
            System.out.println("matter.fallback: " + req);
            return new int[0];
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
    public <TT> TT select(Db4j.Utils.QueryFunction<TT> body) throws Pausable {
        return db4j.submit(body).await().val;
    }
    public void call(Db4j.Utils.QueryCallable body) throws Pausable {
        db4j.submitCall(body).await();
    }
    static ArrayList<Integer> getall(Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0)).getall(cc -> cc.val);
    }
    static <CC extends Bmeta.Context<KK,VV,CC>,KK,VV> ArrayList<VV> getall(Transaction txn,
            Bmeta<CC,KK,VV,?> map,KK key) throws Pausable {
        CC context = map.context().set(txn).set(key,null);
        return map.findPrefix(context).getall(cc -> cc.val);
    }
    static Btree.Range<Btrees.II.Data> prefix(Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0));
    }
    int krow(Transaction txn,String key) throws Pausable {
        return dm.idmap.find(txn,key);
    }
    
    static class Spawner<TT> {
        public Spawner() {}
        public Spawner(boolean $skipNulls) { skipNulls = $skipNulls; }
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

    // fixme - txc should have ?page/per_page
    { add(routes.txc,this::teamChannels); }
    public Object teamChannels(String teamid) throws Pausable {
        Integer kteam = get(dm.idmap,teamid);
        if (kteam==null)
            return null;
        ArrayList<Channels> channels = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Btrees.II.Data> range = dm.chanByTeam.findPrefix(dm.chanByTeam.context().set(txn).set(kteam,0));
            while (range.next()) {
                Channels chan = dm.getChan(txn,range.cc.val);
                if (chan.deleteAt==0)
                    channels.add(chan);
            }
        }).await();
        return map(channels,chan -> chan2reps.copy(chan),HandleNulls.skip);
    }



    { add(routes.license,() -> set(new LicenseClientFormatOldReps(),x->x.isLicensed="false")); }
    { add(routes.websocket,() -> "not available"); }
    
    
    
    
    enum HandleNulls {
        skip,add,map;
    }

    <SS,TT> ArrayList<TT> map(java.util.List<SS> src,Function<SS,TT> mapping,HandleNulls nulls) {
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
    <SS,TT> ArrayList<TT> mapi(java.util.List<SS> src,BiFunction<SS,Integer,TT> mapping,HandleNulls nulls) {
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
    
    public static class BadRoute extends RuntimeException {
        long statusCode;
        public BadRoute(long $statusCode,String message) {
            super(message);
            statusCode = $statusCode;
        }
    }
    
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
        String channels = "/api/v4/channels";
        String teams = "/api/v4/teams";
        String getTeams = "/api/v4/teams?page/per_page";
        String cmmv = "/api/v4/channels/members/me/view";
        String websocket = "/api/v3/users/websocket";
        String cxs = "/api/v4/channels/{chanid}/stats";
        String txs = "/api/v4/teams/{teamid}/stats";
        String invite = "/api/v3/teams/add_user_to_team_from_invite";
        String license = "/api/v4/license/client?format";
        String status = "/api/v4/users/{userid}/status";
        String image = "/api/v3/users/{userid}/image?*";
        String config = "/api/v4/config/client?format";
        String users = "/api/v4/users";
        String usersIds = "/api/v4/users/ids";
        String channelUsers = "/api/v4/users?in_channel/page/per_page";
        String allUsers = "/api/v4/users?page/per_page";
        String teamUsers = "/api/v4/users?in_team/page/per_page/*";
        String nonTeamUsers = "/api/v4/users?not_in_team/page/per_page";
        String login = "/api/v3/users/login";
        String login4 = "/api/v4/users/login";
        String um = "/api/v4/users/me";
        String umPreferences = "/api/v4/users/me/preferences";
        String unread = "/api/v3/teams/unread";
        String umtu = "/api/v4/users/me/teams/unread";
        String txmi = "/api/v4/teams/{teamid}/members/ids";
        String txc = "/api/v4/teams/{teamid}/channels";
        String usi = "/api/v4/users/status/ids";
        String cxm = "/api/v4/channels/{chanid}/members";
        String cxmx = "/api/v4/channels/{chanid}/members/{userid}";
        String txmx = "/api/v4/teams/{teamid}/members/{userid}";
        String cxmi = "/api/v4/channels/{chanid}/members/ids";
        String searchPosts = "/api/v3/teams/{teamid}/posts/search";
        String createPosts = "/api/v3/teams/{teamid}/channels/{chanid}/posts/create";
        String getPosts = "/api/v3/teams/{teamid}/channels/{chanid}/posts/page/{first}/{num}";
        String postsAfter = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/after/{first}/{num}";
        String postsBefore = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/before/{first}/{num}";
        String pinPost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/pin";
        String unpinPost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/unpin";
        String deletePost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/delete";
        String getPinned = "/api/v3/teams/{teamid}/channels/{chanid}/pinned";
        String getFlagged = "/api/v3/teams/{teamid}/posts/flagged/{first}/{num}";
        String updatePost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/update";
        String permalink = "/api/v3/teams/{teamid}/pltmp/{postid}";
        String txmBatch = "/api/v4/teams/{teamid}/members/batch";
        String teamsMe = "/api/v3/teams/{teamid}/me";
        String oldTembers = "/api/v3/teams/members";
        String direct = "/api/v4/channels/direct";
        String uxPreferences = "/api/v4/users/{userid}/preferences";
        String savePreferences = "/api/v3/preferences/save";
        String deletePrefs = "/api/v3/preferences/delete";
        String createGroup = "/api/v3/teams/{teamid}/channels/create_group";
        String txcName = "/api/v4/teams/{teamid}/channels/name/{channelName}";
        String txcxmx = "/api/v3/teams/{teamid}/channels/{chanid}/members/{userid}";
        String autoUser = "/api/v4/users/autocomplete?in_team/in_channel/name"; // -> [users]
        String txcSearch = "/api/v4/teams/{teamid}/channels/search"; // post {term:} -> channel
        String upload = "/api/v3/teams/{teamid}/files/upload";
        String patch = "/api/v4/users/me/patch";
        String search = "/api/v4/users/search";
        String deleteReaction = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/reactions/delete";
        String saveReaction = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/reactions/save";
        String reactions = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/reactions";
    }
    static Routes routes = new Routes();

    static String [] TOWN = new String[] { "town-square", "Town Square" };
    static String [] TOPIC = new String[] { "off-topic", "Off-Topic" };
    
    public Channels newChannel(String teamId,String name,String display,String type) {
        Channels x = new Channels();
        x.createAt = x.updateAt = x.extraUpdateAt = new java.util.Date().getTime();
        x.displayName = display;
        x.name = name;
        x.id = matter.newid();
        x.teamId = teamId;
        x.type = type;
        return x;
    }

    public Posts newPost(String message,String chanid) {
        Posts post = new Posts();
        post.message = message;
        post.channelId = chanid;
        return post;        
    }
    public Posts newPost(Posts x,String uid,String [] fileIds) {
        x.id = matter.newid();
        x.createAt = x.updateAt = timestamp();
        x.fileIds = fileIds==null ? new String[0]:fileIds;
        x.userId = uid;
        return x;
    }

    static public ChannelMembers newChannelMember(String uid,String cid) {
        ChannelMembers cm = new ChannelMembers();
        cm.userId = uid;
        cm.channelId = cid;
        cm.roles = "channel_user";
        return cm;
    }
    static public TeamMembers newTeamMember(String teamId,String uid) {
        TeamMembers tm = new TeamMembers();
        tm.userId = uid;
        tm.teamId = teamId;
        tm.roles = "team_user";
        return tm;
    }

    boolean isDirect(Channels chan) { return chan.type.equals("D"); }
    boolean isOpenGroup(Channels chan) { return chan.type.equals("O"); }
    
    static String userNotifyFmt =
            "{\"channel\":\"true\",\"desktop\":\"all\",\"desktop_sound\":\"true\",\"email\":\"true\","
            + "\"first_name\":\"false\",\"mention_keys\":\"%s\",\"push\":\"mention\"}";
    
    static String userNotify(Users user) {
        String keys = user.username + ",@" + user.username;
        return String.format(userNotifyFmt,keys);
    }
    
    
    static String cemberProps =
            "{\"desktop\":\"default\",\"email\":\"default\",\"mark_unread\":\"all\",\"push\":\"default\"}";
    
    static String literal = "{desktop: \"default\", email: \"default\", mark_unread: \"all\", push: \"default\"}";
    static<TT> TT either(TT v1,TT v2) { return v1==null ? v2:v1; }
    static boolean either(int val,int v1,int v2) { return val==v1 | val==v2; }
    

    static MatterData.FieldCopier<TeamsxChannelsxPostsCreateReqs,Posts> req2posts =
            new MatterData.FieldCopier(TeamsxChannelsxPostsCreateReqs.class,Posts.class);
    static MatterData.FieldCopier<Posts,Xxx> posts2rep =
            new MatterData.FieldCopier<>(Posts.class,Xxx.class,(src,dst) -> {
                dst.props = MatterControl.parser.parse(either(src.props,"{}"));
            });
    static MatterData.FieldCopier<Reaction,Reactions> req2reactions =
            new MatterData.FieldCopier<>(Reaction.class,Reactions.class);
    static MatterData.FieldCopier<Reactions,Reaction> reactions2rep =
            new MatterData.FieldCopier<>(Reactions.class,Reaction.class);
    static MatterData.FieldCopier<Users,mm.rest.User> users2userRep =
            new MatterData.FieldCopier<>(Users.class,mm.rest.User.class);
    static MatterData.FieldCopier<Status,UsersStatusIdsRep> status2reps =
            new MatterData.FieldCopier(Status.class,UsersStatusIdsRep.class);
    static MatterData.FieldCopier<TeamsReqs,Teams> req2teams =
            new MatterData.FieldCopier(TeamsReqs.class,Teams.class);
    static MatterData.FieldCopier<Teams,TeamsReps> team2reps =
            new MatterData.FieldCopier<>(Teams.class,TeamsReps.class);
    static MatterData.FieldCopier<TeamMembers,TeamsMembersRep> tember2reps =
            new MatterData.FieldCopier(TeamMembers.class,TeamsMembersRep.class);
    MatterData.FieldCopier<ChannelsReqs,Channels> req2channel =
            new MatterData.FieldCopier<>(ChannelsReqs.class,Channels.class,(src,dst) -> {
                dst.createAt = dst.updateAt = timestamp();
                dst.id = matter.newid();
            });
    static MatterData.FieldCopier<Channels,ChannelsReps> chan2reps =
            new MatterData.FieldCopier<>(Channels.class,ChannelsReps.class);
    static MatterData.FieldCopier<ChannelMembers,ChannelsxMembersReps> cember2reps =
            new MatterData.FieldCopier<>(ChannelMembers.class,ChannelsxMembersReps.class,(src,dst) -> {
                dst.notifyProps = MatterControl.parser.parse(either(src.notifyProps,literal));
            });
    static MatterData.FieldCopier<UsersReqs,Users> req2users = new MatterData.FieldCopier(UsersReqs.class,Users.class);
    static MatterData.FieldCopier<Users,UsersReps> users2reps
            = new MatterData.FieldCopier<>(Users.class,UsersReps.class,(src,dst) -> {
                dst.notifyProps = MatterControl.parser.parse(either(src.notifyProps,userNotify(src)));
            });
    static MatterData.FieldCopier<PreferencesSaveReq,Preferences> req2prefs
            = new MatterData.FieldCopier(PreferencesSaveReq.class,Preferences.class);
    static MatterData.FieldCopier<Preferences,PreferencesSaveReq> prefs2rep
            = new MatterData.FieldCopier(Preferences.class,PreferencesSaveReq.class);

    
    
    public Object process(Session session,HttpRequest req,HttpResponse resp) throws Pausable, Exception {
        String uri = req.uriPath;
        
        Object robj = route(session,req,resp);
        if (robj != routeNotFound) return robj;
        
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
        String path = (uri!=null && uri.startsWith("/static/")) ? uri.replace("/static",""):"/root.html";
        return new File(base+path);
    }
    boolean yoda = true;
    public class Session extends HttpSession {
    public void execute() throws Pausable, Exception {
        try {
            // We will reuse the req and resp objects
            HttpRequest req = new HttpRequest();
            HttpResponse resp = new HttpResponse();
            while (true) {
                super.readRequest(req);
                if (req.keepAlive())
                    resp.addField("Connection", "Keep-Alive");

                Object reply = null;
                if (req.uriPath==null || ! req.uriPath.startsWith("/api/"))
                    serveFile(req,resp);
                else if (yoda)
                try {
                    reply = process(this,req,resp);
                }
                catch (BadRoute ex) {
                    resp.status = HttpResponse.ST_BAD_REQUEST;
                    UsersLogin4Error error = new UsersLogin4Error();
                    error.message = ex.getMessage();
                    error.statusCode = ex.statusCode;
                    reply = error;
                }
                else
                try {
                    reply = process(this,req,resp);
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
    public void serveFile(HttpRequest req,HttpResponse resp) throws Exception, Pausable {
        File f = urlToPath(req);
        boolean headOnly = req.method.equals("HEAD");
        sendFile(resp, f, headOnly);
    }
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
    }
    Session sess = new Session();
    public static void main(String[] args) throws Exception {
        MatterFull.main(args);
    }
}
