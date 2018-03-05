package foobar;

import static foobar.MatterControl.gson;
import static foobar.MatterControl.set;
import static foobar.MatterControl.skipGson;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kilim.Pausable;
import kilim.Task;
import kilim.http.HttpResponse;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.Posts;
import mm.data.Preferences;
import mm.data.Reactions;
import mm.data.Sessions;
import mm.data.Status;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.rest.ChannelsReps;
import mm.rest.ChannelsReqs;
import mm.rest.ChannelsxMembersReps;
import mm.rest.NotifyUsers;
import mm.rest.PreferencesSaveReq;
import mm.rest.Reaction;
import mm.rest.TeamsMembersRep;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.TeamsxChannelsxPostsCreateReqs;
import mm.rest.UsersLogin4Error;
import mm.rest.UsersReps;
import mm.rest.UsersReqs;
import mm.rest.UsersStatusIdsRep;
import mm.rest.Xxx;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Db4j;
import org.srlutils.Simple;

public class Utilmm {
    static String expires(Object val) {
        if (val instanceof String)
            return (String) val;
        Double days = (double) val;
        Date expdate = new Date();
        long ticks = expdate.getTime() + (long)(days*24*3600*1000);
        expdate.setTime(ticks);
        DateFormat df = new SimpleDateFormat("dd MMM yyyy kk:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        String cookieExpire = "expires=" + df.format(expdate);
        return cookieExpire;
    }

    /**
     * format a cookie and add it to the response
     * @param resp the response to add the cookie to
     * @param name the name of the cookie
     * @param value the value
     * @param days either a double, in which case the number of days till expiration, or a string literal to insert
     * @param httponly whether the cookie should be set httponly
     */
    static void setCookie(HttpResponse resp,String name,String value,Object days,boolean httponly) {
        String expires = expires(days);
        String newcookie = String.format("%s=%s; Path=/; %s",name,value,expires);
        if (httponly) newcookie += "; HttpOnly";
        System.out.println("Set-Cookie: "+newcookie);
        resp.addField("Set-Cookie",newcookie);
    }
    
    static String parse(String sub,String name) {
        return sub.startsWith(name) ? sub.substring(name.length()) : null;
    }

    static String [] getCookies(String cookie,String ... names) {
        boolean dbg = false;
        String [] vals = new String[names.length];
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
                for (int jj=0; jj < names.length; jj++)
                    if ((part=parse(sub,names[jj])) != null)
                        vals[jj] = part;
            }
        }
        return vals;
    }

    static class Regexen {
        // https://docs.oracle.com/javase/tutorial/essential/regex/unicode.html
        // https://www.regular-expressions.info/posixbrackets.html
        // https://www.regular-expressions.info/unicode.html
        static Pattern hashtag = Pattern.compile("\\B#(\\p{L}[\\w-_.]{1,}\\w)");
        static Pattern mention = Pattern.compile("(?:\\B@|\\b)(\\w+)");
    }

    /**
     * extract hashtags from text, add them to tags and then return the space-join of tags
     * @param text the string to scan
     * @param tags the list to add to and to join, ie it should probably be empty initially
     * @return the join of tags with a space as separator
     */
    static String getHashtags(String text,Collection<String> tags) {
        Matcher mat = Regexen.hashtag.matcher(text);
        while (mat.find())
            tags.add(mat.group(0));
        return String.join(" ",tags);
    }

    static ArrayList<String> getUserNicks(Users user) { return getUserNicks(user,new ArrayList()); }
    static ArrayList<String> getUserNicks(Users user,ArrayList<String> list) {
        if (user.notifyProps==null)
            return set(list, x -> { x.add(user.username); x.add("@"+user.username); });
        NotifyUsers notify = gson.fromJson(user.notifyProps,NotifyUsers.class);
        list.add("@"+user.username);
        if (notify.firstName & user.firstName != null && user.firstName.length() > 0)
            list.add(user.firstName);
        for (String key : notify.mentionKeys.split(","))
            list.add(key);
        return list;
    }
    static ArrayList<Integer> getall(Db4j.Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0)).getall(cc -> cc.val);
    }
    static Btree.Range<Btrees.II.Data> prefix(Db4j.Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0));
    }
    
    static class Spawner<TT> {
        public Spawner() {}
        public Spawner(boolean $skipNulls) { skipNulls = $skipNulls; }
        boolean skipNulls = true;
        ArrayList<Task.Spawn<TT>> tasks = new ArrayList();
        Task.Spawn<TT> spawn(Pausable.Spawn<TT> body) {
            Task.Spawn task = Task.spawn(body);
            tasks.add(task);
            return task;
        }
        ArrayList<TT> join() throws Pausable {
            ArrayList<TT> vals = new ArrayList();
            for (Task.Spawn<TT> task : tasks) {
                TT val = task.join().result;
                if (val != null | !skipNulls)
                    vals.add(val);
            }
            return vals;
        }
    }
    /**
     * filter a list, wrapping the selected items in a row, ie an index-value tuple
     * @param <TT> the type of the list
     * @param list the list
     * @param filter the filter to apply, return true to select the item
     * @return the selected indices and items
     */
    static <TT> ArrayList<MatterData.Row<TT>> filterRows(java.util.Collection<TT> list,Function<TT,Boolean> filter) {
        ArrayList<MatterData.Row<TT>> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(item))
                result.add(new MatterData.Row(ii,item));
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
    static <TT,UU> ArrayList<UU> map(java.util.Collection<TT> list,Function<TT,UU> filter) {
        ArrayList<UU> result = new ArrayList<>();
        for (TT item : list)
            result.add(filter.apply(item));
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
    static enum HandleNulls {
        skip,add,map;
    }

    static <SS,TT> ArrayList<TT> map(java.util.List<SS> src,Function<SS,TT> mapping,HandleNulls nulls) {
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
    static <SS,TT> ArrayList<TT> mapi(java.util.List<SS> src,BiFunction<SS,Integer,TT> mapping,HandleNulls nulls) {
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
    static<TT> TT either(TT v1,TT v2) { return v1==null ? v2:v1; }
    static<TT> TT either(TT v1,Supplier<TT> v2) { return v1==null ? v2.get():v1; }
    static boolean either(int val,int v1,int v2) { return val==v1 | val==v2; }

    public interface Ready<TT> {
        public boolean test(TT obj);
    }
    public interface Check<TT> {
        public Object test(TT obj);
    }
    
    public static Sessions newSession(String userid) {
        Sessions session = new Sessions();
        session.createAt = timestamp();
        session.expiresAt = session.createAt + 300*24*3600*1000;
        session.id = newid();
        session.userId = userid;
        return session;
    }
    public static Channels newChannel(String teamId,String name,String display,String type,String uid) {
        Channels x = new Channels();
        x.createAt = x.updateAt = x.extraUpdateAt = new java.util.Date().getTime();
        x.displayName = display;
        x.name = name;
        x.id = newid();
        x.teamId = teamId;
        x.type = type;
        x.creatorId = uid;
        return x;
    }

    public static Posts newPost(String message,String chanid) {
        Posts post = new Posts();
        post.message = message;
        post.channelId = chanid;
        return post;        
    }
    public static Posts newPost(Posts x,String uid,String [] fileIds) {
        x.id = newid();
        x.createAt = x.updateAt = timestamp();
        x.fileIds = fileIds==null ? new String[0]:fileIds;
        x.userId = uid;
        return x;
    }
    interface Typeable {
        void prop(PropsAll prop,String old,String update);
    }
    interface Messageable {
        String prop(String username,String victim);
    }
    interface Chanable {
        String get(Channels chan);
    }
    static enum PostsTypes {

        system_join_channel       (null,                          (u,v) -> u+" has joined the channel."),
        system_leave_channel      (null,                          (u,v) -> u+" has left the channel."),
        system_add_to_channel     ((p,u,v) -> p.addedUsername  =v,(u,v) -> v+" added to the channel by "+u),
        system_remove_from_channel((p,u,v) -> p.removedUsername=v,(u,v) -> v+" was removed from the channel."),

        system_header_change("header",(p,o,u) -> { p.old_header=o; p.new_header=u; },chan->chan.header),
        system_channel_deleted,
        system_displayname_change("display name",(p,o,u) -> { p.old_displayname=o; p.new_displayname=u; },chan->chan.displayName),
        system_purpose_change("purpose",(p,o,u) -> { p.old_purpose=o; p.new_purpose=u; },chan->chan.purpose);

        String field;
        Typeable setter;
        Messageable messager;
        Chanable channer;
        PostsTypes(String $field,Typeable $setter,Chanable $channer) { field=$field; setter=$setter; channer=$channer; }
        PostsTypes() {}
        PostsTypes(Typeable $setter,Messageable $messager) { setter=$setter; messager=$messager; }
        boolean changed(Channels v0,Channels v1) {
            String old = channer.get(v0);
            String update = channer.get(v1);
            if (old==update | blank(old) & blank(update)) return false;
            if (old==null | update==null) return true;
            return ! update.equals(old);
        }
        public Posts gen(String uid,String chanid,String username,Channels v0,Channels v1) {
            String old = channer.get(v0);
            String update = channer.get(v1);
            PostsTypes type = this;
            String fmt =
                    blank(update) ? "%s removed the channel %s (was: %s)"
                    : blank(old)  ? "%s updated the channel %s to: %4$s"
                    :               "%s updated the channel %s from: %s to: %s";
            String msg = String.format(fmt,username,type.field,old,update);
            Posts post = newPost(msg,chanid);
            newPost(post,uid,null);
            post.type = type.name();
            PropsAll props = new PropsAll(username);
            type.setter.prop(props,old,update);
            post.props = gson.toJson(props);
            return post;
        }
        public Posts cember(String username,String victim,String uid,String chanid) {
            // per sniffing, posts have no mentions and no tags
            Posts post = new Posts();
            post.message = messager.prop(username,victim);
            post.channelId = chanid;
            newPost(post,uid,null);
            post.type = name();
            PropsAll props;
            if (setter==null)
                props = new PropsAll(username);
            else {
                props = new PropsAll();
                setter.prop(props,username,victim);
            }
            post.props = skipGson.toJson(props);
            return post;
        }
    }

    static enum PrefsTypes {
        // mysql: select distinct Category from Preferences;
        advanced_settings,
        direct_channel_show,
        display_settings,
        flagged_post,
        group_channel_show,
        tutorial_step;
        boolean test(Preferences pref) {
            return name().equals(pref.category);
        }
        boolean test(Preferences pref,String value) {
            return test(pref) & value.equals(pref.value);
        }
    }
    static boolean blank(String val) { return val==null || val.length()==0; }
    // display, header, purpose, join and add: in addition to the action-specific fields, username is provided
    public static class PropsAll {
        public String username;
        public String addedUsername;
        public String removedUsername;
        public String new_header;
        public String old_header;
        public String new_displayname;
        public String old_displayname;
        public String new_purpose;
        public String old_purpose;
        public PropsAll() {}
        public PropsAll(String $username) { username=$username; }
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
    
    
    static String decamelify(String text) {
        Matcher mat = Pattern.compile("(?<=[a-z])[A-Z]").matcher(text);
        StringBuffer buf = new StringBuffer();
        while (mat.find())
            mat.appendReplacement(buf, "_"+mat.group());
        mat.appendTail(buf);
        return buf.toString();
    }
    
    public static class FieldCopier<SS,TT> {
        Field[] map, srcFields;
        Class <TT> dstClass;
        BiConsumer<SS,TT> [] extras;
        
        public TT copy(SS src) {
            return copy(src,null);
        }
        public <XX extends TT> XX copy(SS src,XX dst) {
            if (src==null) return dst;
            if (dst==null) dst = (XX) Simple.Reflect.alloc(dstClass,true);
            try {
                for (int ii=0; ii < srcFields.length; ii++)
                    if (map[ii] != null)
                        map[ii].set(dst, srcFields[ii].get(src));
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
            for (BiConsumer extra : extras)
                extra.accept(src,dst);
            return dst;
        }
        public FieldCopier(Class<SS> srcClass,Class<TT> dstClass,BiConsumer<SS,TT> ... extras) {
            this.extras = extras;
            this.dstClass = dstClass;
            srcFields = srcClass.getDeclaredFields();
            Field[] dstFields = dstClass.getDeclaredFields();
            map = new Field[srcFields.length];
            for (int ii=0; ii < srcFields.length; ii++)
                for (int jj=0; jj < dstFields.length; jj++) {
                    Field src = srcFields[ii], dst = dstFields[jj];
                    if (src.getName().equals(dst.getName()) & src.getType().equals(dst.getType())) {
                        src.setAccessible( true );
                        dst.setAccessible( true );
                        map[ii] = dst;
                    }
                }
        }
    }
    
    static public class Box<TT> {
        public TT val;
        public Box() {};
        public Box(TT $val) { val = $val; }
    }
    public static <TT> Box<TT> box() { return new Box(); }
    static public class Ibox {
        public int val;
        public Ibox() {};
        public Ibox(int $val) { val = $val; };
    }
    public static Ibox ibox() { return new Ibox(); }

    
    // fixme - should random be stronger/non-deterministic ?
    static Random random = new Random();
    static final int idlen = 26;
    static String newid() {
        String val = "";
        while (val.length() != idlen)
            val = new BigInteger(134,random).toString(36);
        System.out.println("newid: " + val);
        return val;
    }
    static <TT> TT [] append(TT [] src,TT ... other) {
        TT[] dst = org.srlutils.Util.dup(src,0,src.length+other.length);
        org.srlutils.Util.dup(other,0,other.length,dst,src.length);
        return dst;
    }

    public static class HttpStatus extends RuntimeException {
        byte [] status;
        public HttpStatus(String message,byte [] $status) {
            super(message);
            status = $status;
        }
        Object route(HttpResponse resp) {
            resp.status = status;
            return null;
        }
    }
    public static class Http304 extends HttpStatus {
        public Http304() {
            super("",HttpResponse.ST_NOT_MODIFIED);
        }
    }
    public static class BadRoute extends HttpStatus {
        long statusCode;
        public BadRoute(long $statusCode,String message,byte [] $status) {
            super(message,$status);
            statusCode = $statusCode;
        }
        public BadRoute(long $statusCode,String message) {
            this($statusCode,message,HttpResponse.ST_BAD_REQUEST);
        }
        Object route(HttpResponse resp) {
            resp.status = status;
            UsersLogin4Error error = new UsersLogin4Error();
            error.message = getMessage();
            error.statusCode = statusCode;
            return error;
        }
    }
    static String [] TOWN = new String[] { "town-square", "Town Square" };
    static String [] TOPIC = new String[] { "off-topic", "Off-Topic" };
    

    static boolean isDirect(Channels chan) { return chan.type.equals("D"); }
    static boolean isOpenGroup(Channels chan) { return chan.type.equals("O"); }
    /**
     * is chan a full channel, ie either open or private, as opposed to a direct or group channel
     * @param chan the channel to test
     * @return true if chan is a full channel
     */
    static boolean isFull(Channels chan) { return chan.type.equals("O") | chan.type.equals("P"); }
    
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
    

    static FieldCopier<TeamsxChannelsxPostsCreateReqs,Posts> req2posts =
            new FieldCopier(TeamsxChannelsxPostsCreateReqs.class,Posts.class);
    static FieldCopier<Posts,Xxx> posts2rep =
            new FieldCopier<>(Posts.class,Xxx.class,(src,dst) -> {
                dst.props = MatterControl.parser.parse(either(src.props,"{}"));
                if (src.fileIds.length > 0)
                    dst.fileIds = Arrays.asList(src.fileIds);
            });
    static FieldCopier<Reaction,Reactions> req2reactions =
            new FieldCopier<>(Reaction.class,Reactions.class);
    static FieldCopier<Reactions,Reaction> reactions2rep =
            new FieldCopier<>(Reactions.class,Reaction.class);
    static FieldCopier<Users,mm.rest.User> users2userRep =
            new FieldCopier<>(Users.class,mm.rest.User.class);
    static FieldCopier<Status,UsersStatusIdsRep> status2reps =
            new FieldCopier(Status.class,UsersStatusIdsRep.class);
    static FieldCopier<TeamsReqs,Teams> req2teams =
            new FieldCopier(TeamsReqs.class,Teams.class);
    static FieldCopier<TeamsReps,Teams> reps2teams =
            new FieldCopier<>(TeamsReps.class,Teams.class);
    static FieldCopier<Teams,TeamsReps> team2reps =
            new FieldCopier<>(Teams.class,TeamsReps.class);
    static FieldCopier<TeamMembers,TeamsMembersRep> tember2reps =
            new FieldCopier(TeamMembers.class,TeamsMembersRep.class);
    static FieldCopier<ChannelsReqs,Channels> req2channel =
            new FieldCopier<>(ChannelsReqs.class,Channels.class,(src,dst) -> {
                dst.createAt = dst.updateAt = timestamp();
                dst.id = newid();
            });
    static FieldCopier<Channels,ChannelsReps> chan2reps =
            new FieldCopier<>(Channels.class,ChannelsReps.class);
    static FieldCopier<Channels,Channels> chan2chan =
            new FieldCopier<>(Channels.class,Channels.class);
    static FieldCopier<ChannelMembers,ChannelsxMembersReps> cember2reps =
            new FieldCopier<>(ChannelMembers.class,ChannelsxMembersReps.class,(src,dst) -> {
                dst.notifyProps = MatterControl.parser.parse(either(src.notifyProps,literal));
            });
    static FieldCopier<UsersReqs,Users> req2users = new FieldCopier(UsersReqs.class,Users.class);
    static FieldCopier<Users,UsersReps> users2reps
            = new FieldCopier<>(Users.class,UsersReps.class,(src,dst) -> {
                dst.notifyProps = MatterControl.parser.parse(either(src.notifyProps,userNotify(src)));
            });
    static FieldCopier<PreferencesSaveReq,Preferences> req2prefs
            = new FieldCopier(PreferencesSaveReq.class,Preferences.class);
    static FieldCopier<Preferences,PreferencesSaveReq> prefs2rep
            = new FieldCopier(Preferences.class,PreferencesSaveReq.class);


    public static String subdomain(String host) {
        if (host==null) return host;
        String [] parts = host.split("\\.");
        return parts.length==0 ? null:parts[0];
    }

    // https://sashat.me/2017/01/11/list-of-20-simple-distinct-colors/    
    static String[] svgColors = new String[] {
        "#e6194b","#3cb44b","#ffe119","#0082c8","#f58231","#911eb4","#46f0f0","#f032e6","#d2f53c","#fabebe",
        "#008080","#e6beff","#aa6e28", /*"#fffac8",*/ "#800000", /*"#aaffc3",*/
        "#808000",/*"#ffd8b1",*/ "#000080","#808080",
        // "#FFFFFF","#000000"
    };
    static String svgTemplate
            = "<svg version=\"1.1\" width=\"100\" height=\"100\" xmlns=\"http://www.w3.org/2000/svg\">\n"
            +"  <rect width=\"100%%\" height=\"100%%\" fill=\"%s\" />\n"
            +"  <text x=\"50%%\" y=\"50%%\" dy=\".35em\" font-size=\"50\" text-anchor=\"middle\" fill=\"white\">%s</text>\n"
            +"</svg>\n";
    public static String genSvg(int colorCode,String initials) {
        int index = Math.floorMod(colorCode,svgColors.length);
        String color = svgColors[index];
        return String.format(svgTemplate,color,initials);
    }

    public static String makeFilename(String name) {
        return "db_files/files/" + name;
    }

    public static String makeInitials(String user,String first,String last) {
        return first==null | last==null
                ? user.substring(0,2)
                : first.substring(0,1) + last.substring(0,1);
    }
    public static void cacheControl(HttpResponse resp,int age) {
        resp.addField("Cache-Control","max-age="+age+", public");
    }
    public static void cacheControl(HttpResponse resp,double age) { cacheControl(resp,(int) age); }
    public static <TT> boolean equals(TT obj1,TT obj2){ return obj1==null ? obj1==obj2 : obj1.equals(obj2); }
    public interface Socketable {
        void run(MatterWebsocket ws);
    }
    public static class Tuple2<T1,T2> {
        T1 v1;
        T2 v2;
        Tuple2(T1 $v1,T2 $v2) { v1=$v1; v2=$v2; }
    }
}
