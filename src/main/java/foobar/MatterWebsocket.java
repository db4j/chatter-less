package foobar;

import com.google.gson.JsonElement;
import static foobar.MatterControl.gson;
import static foobar.Utilmm.*;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Spawnable;
import kilim.Task;
import mm.data.Sessions;
import mm.rest.PreferencesSaveReq;
import mm.rest.Reaction;
import mm.rest.Xxx;
import mm.ws.client.Client;
import mm.ws.server.AddedToTeamData;
import mm.ws.server.Broadcast;
import mm.ws.server.ChannelDeletedData;
import mm.ws.server.DirectAddedData;
import mm.ws.server.GroupAddedData;
import mm.ws.server.HelloData;
import mm.ws.server.LeaveTeamData;
import mm.ws.server.NewUserData;
import mm.ws.server.PostDeletedData;
import mm.ws.server.PostEditedData;
import mm.ws.server.PostedData;
import mm.ws.server.PreferencesChangedData;
import mm.ws.server.ReactionAddedData;
import mm.ws.server.Response;
import mm.ws.server.StatusChangeData;
import mm.ws.server.TypingData;
import mm.ws.server.UpdateTeamData;
import mm.ws.server.UserAddedData;
import mm.ws.server.UserRemovedData;
import mm.ws.server.UserUpdatedData;
import org.db4j.Db4j;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.srlutils.Simple;

public class MatterWebsocket extends WebSocketServlet {
    MatterControl matter;
    MatterData dm;
    Db4j db4j;
    
    MatterWebsocket() {
    }

    MatterWebsocket(MatterControl $matter) {
        matter = $matter;
        dm = matter.dm;
        db4j = matter.db4j;
    }

    Mailbox<Relayable> mbox = new Mailbox<>();
    int numBoxFail = 0;

    interface Relayable {
        void run() throws Pausable;
    }
    /**
     * attempt to send the runnable to the relay.
     * note: the name is overloaded to allow find usages for all mbox access
     * @param dummy
     * @param obj 
     */
    boolean add(boolean dummy,Relayable obj) {
        // fixme - decide on a meaningful overflow policy
        // mbox size is max integer, so for this to fail things already need to be fubar
        // it's all but impossible to imagine this happening in real life
        // and if it does, it's not clear what the best recourse is
        // eg, it might make sense to shut the server down since it's already fucked
        // for now, just sleep the supplier thread and warn
        while (! mbox.putnb(obj)) {
            numBoxFail++;
            System.out.println("matter.ws::MailboxFull -- this is unexpected and should be investigated, sleeping ...");
            Simple.sleep(1000);
        }
        return true;
    }
    void add(int delay,Relayable obj) throws Pausable {
        if (delay > 0) Task.sleep(delay);
        mbox.put(obj);
    }

    // chan and team need to be distinct because kchan and kteams are not orthogonal
    TreeMap<Integer,LinkedList<Message>> channelMessages = new TreeMap();
    TreeMap<Integer,LinkedList<Message>> teamMessages = new TreeMap();
    LinkedHashMap<Integer,EchoSocket> sockets = new LinkedHashMap<>();
    EchoSocket [] active;
    int nactive;
    { growActive(); }
    
    static int perActive = 1024;
    
    void growActive() {
        active = new EchoSocket[perActive];
        nactive = 0;
    }

    void removeSession(EchoSocket key) {
        EchoSocket echo = sockets.get(key.kuser);
        // an NPE occurred without this check, it's not readily apparent how that came to pass
        //   prolly some sort of timing/race but would be nice to quantify
        if (echo==null)
            return;
        if (echo==key) {
            if (echo.next==null) sockets.remove(echo.kuser);
            else sockets.put(echo.kuser,echo.next);
            return;
        }
        while (echo.next != key) echo = echo.next;
        echo.next = key.next;
    }
    EchoSocket session(int kuser,EchoSocket session,boolean remove) {
        relayOnly();
        if (remove) removeSession(session);
        else if (session==null) return sockets.get(kuser);
        else session.next = sockets.put(kuser,session);
        return null;
    }

    int teamDelay = 500;
    int channelDelay = 500;
    int usersDelay = 500;
    
    void addChannel(int kchan,Message msg,int [] others) {
        relayOnly();
        LinkedList<Message> alloc = addToMap(channelMessages,kchan,msg);
        if (alloc != null) 
            spawnQuery(db4j.submit(txn ->
                        dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true)).getall(x -> x.key.v2)),
                query -> {
                    for (int other : others)
                        query.val.add(other);
                    add(channelDelay,() -> addChanUsers(kchan,query.val,alloc));
                });
    }
    // fixme - delays should really be relative to true time, not post-query time
    void addTeam(int kteam,Message text,int [] others) {
        relayOnly();
        LinkedList<Message> alloc = addToMap(teamMessages,kteam,text);
        if (alloc != null) 
            spawnQuery(db4j.submit(txn ->
                        dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true)).getall(x -> x.key.v2)),
                query -> {
                    for (int other : others)
                        query.val.add(other);
                    add(teamDelay,() -> addTeamUsers(kteam,query.val,alloc));
                });
    }    
    
    int maxPending = 5;

    // false adds are allowed so long as they're fairly rare (even under load)
    // a failure to add is more severe
    Integer addActive(EchoSocket echo) {
        relayOnly();
        if (nactive==perActive) growActive();
        EchoSocket [] echos = active;
        echos[nactive++] = echo;
        if (nactive==1)
            Task.spawnCall(() -> add(usersDelay,() -> runUsers(echos)));
        return null;
    }
    
    void runUsers(EchoSocket [] echos) {
        relayOnly();
        for (EchoSocket echo : echos)
            if (echo==null) break;
            else echo.runUser();
        if (echos==active)
            growActive();
    }

    public class RelayTask extends kilim.Task {
        Thread thread;
        public void execute() throws Pausable,Exception {
            thread = Thread.currentThread();
            while (true) {
                Relayable runnee = mbox.get();
                try { runnee.run(); }
                catch (Exception ex) {
                    System.out.println("matter.ws.relay: "+ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }
    Scheduler sched = new Scheduler(1);
    RelayTask relay = new RelayTask();
    { relay.setScheduler(sched).start(); }

    boolean dbg = true;
    void relayOnly() {
        if (dbg)
            Simple.softAssert(relay.thread.equals(Thread.currentThread()));
    }
    
    public static <QQ extends Db4j.Query> void spawnQuery(QQ query,Spawnable.Call1<QQ> map) {
        kilim.Task.spawn(() -> {
            query.await();
            map.execute(query);
            return null;
        });
    }
    


    public Send send = new Send();
    public class Send {
        public void userAdded(String teamId,String userId,String channelId,Integer kchan) {
            UserAddedData brief = new UserAddedData(teamId,userId);
            sendChannel(kchan,channelId,brief,null);
        }
        public void directAdded(String teammateId,String channelId,Integer kchan) {
            DirectAddedData brief = new DirectAddedData(teammateId);
            sendChannel(kchan,channelId,brief,null);
        }
        public void groupAdded(String [] teammateIds,String channelId,Integer kchan) {
            String text = gson.toJson(teammateIds);
            GroupAddedData brief = new GroupAddedData(text);
            sendChannel(kchan,channelId,brief,null);
        }
        public void statusChange(String status,String userId,Integer kuser) {
            StatusChangeData brief = new StatusChangeData(status,userId);
            sendUser(kuser,userId,brief);
        }
        public void newUser(String userId) {
            NewUserData brief = new NewUserData(userId);
            sendAll(brief,null);
        }
        public void preferencesChanged(Integer kuser,String userid,PreferencesSaveReq ... prefs) {
            String text = gson.toJson(prefs);
            PreferencesChangedData brief = new PreferencesChangedData(text);
            sendUser(kuser,userid,brief);
        }
        public void channelDeleted(String channelId,String teamId,Integer kteam) {
            ChannelDeletedData brief = new ChannelDeletedData(channelId);
            sendTeam(kteam,teamId,brief,null);
        }
        public void userRemoved(String removerId,String userId,String channelId,Integer kchan,int kuser) {
            UserRemovedData brief = new UserRemovedData(null,removerId,userId);
            sendChannel(kchan,channelId,brief,others(kuser));
        }
        // fixme - unused and not yet tested, need to implement the http portion of private channels
        public void userRemovedPrivate(String removerId,String userId,String channelId,Integer kuser) {
            UserRemovedData brief = new UserRemovedData(channelId,removerId,null);
            sendUser(kuser,userId,brief);
        }
        public void postEdited(Xxx reply,String chanid,Integer kchan) {
            String text = gson.toJson(reply);
            PostEditedData brief = new PostEditedData(text);
            sendChannel(kchan,chanid,brief,null);
        }
        public void reactionAdded(Reaction reply,boolean save,String chanid,Integer kchan) {
            String text = gson.toJson(reply);
            ReactionAddedData brief = save ? new ReactionAddedData(text) : new ReactionRemovedData(text);
            sendChannel(kchan,chanid,brief,null);
        }
        public void postDeleted(Xxx reply,String chanid,Integer kchan) {
            String text = gson.toJson(reply);
            PostDeletedData brief = new PostDeletedData(text);
            sendChannel(kchan,chanid,brief,null);
        }
        public void posted(Xxx reply,mm.data.Channels chan,String username,Integer kchan,List<String> mentionIds) {
            // fixme - calculate mentions
            // "[\"jgrx8vpqu1kx2tln2vetymjccc\"]";
            String mentions = mentionIds==null ? null:gson.toJson(mentionIds);
            String text = gson.toJson(reply);
            PostedData brief = new PostedData(chan.displayName,chan.name,chan.type,text,username,chan.teamId,mentions);
            sendChannel(kchan,chan.id,brief,null);
        }
        public void userUpdated(mm.rest.User user) {
            UserUpdatedData brief = new UserUpdatedData(user);
            sendAll(brief,omits(user.id));
        }
        public void updateTeam(mm.rest.TeamsReps team) {
            String text = gson.toJson(team);
            UpdateTeamData brief = new UpdateTeamData(text);
            sendAll(brief,null);
        }
        public void typing(String parentId,String userid,String chanid,Integer kchan) {
            TypingData brief = new TypingData(parentId,userid);
            sendChannel(kchan,chanid,brief,omits(userid));
        }
        public void addedToTeam(int kuser,String teamId, String userId) {
            AddedToTeamData brief = new AddedToTeamData(teamId,userId);
            sendUser(kuser,userId,brief);
        }
        public void leaveTeam(String userId,String teamId,Integer kteam,Integer kuser) {
            LeaveTeamData brief = new LeaveTeamData(teamId,userId);
            sendTeam(kteam,teamId,brief,others(kuser));
        }

    }
    public static class ReactionRemovedData extends ReactionAddedData {
        public ReactionRemovedData(String reaction) { super(reaction); }
    }

    // duplicate of mm.ws.Message - use to enable finer-grained gson conversion to json
    public static class Message {
        // data.user (are there others ?) needs "" nulls
        // data.post (etc) needs to be a string with "" nulls
        // broadcast.omit needs to be null if null, else a map
        // data.mentions needs to be skipped if emtpy or null
        static MatterControl mc = null;
        static com.google.gson.JsonParser po = mc.parser;

        public Message(String event,Object data,Broadcast broadcast,long seq) {
            this.event = event;
            // fixme - use gson.toJsonTree(data) instead of converting to a string first
            this.data = po.parse(mc.skipGson.toJson(data));
            this.broadcast = broadcast;
            this.seq = seq;
            map = (TreeMap) broadcast.omitUsers;
        }
        public String event;
        public JsonElement data;
        public Broadcast broadcast;
        public long seq;
    
        private transient String prefix;
        private transient String suffix;
        private transient TreeMap<String,Boolean> map;
    
        public Message prep() {
            String text = mc.nullGson.toJson(this);
            prefix = text.substring(0,text.length()-2);
            suffix = text.substring(text.length()-2);
            return this;
        }
        public String json() { return prefix+suffix; }
        public String json(int seq) { return prefix+seq+"}"; }
        public boolean omit(String userid) { return map==null ? false:map.containsKey(userid); }

        public static void main(String[] args) {
            PostedData brief = new PostedData(
                    "channelDisplayName","channelName","channelType","post","senderName","teamId",null);
            Message m1 = msg(brief,null);
            Message m2 = msg(brief,null,"hello","world");
            System.out.println(m1.json());
            System.out.println(m2.json());
        }
    }
    
    static Message msg(Object obj,Consumer<Broadcast> destify,String ... omits) {
        String klass = obj.getClass().getSimpleName().replace("Data","");
        String event = decamelify(klass).toLowerCase();
        Broadcast broadcast = new Broadcast(omits.length==0 ? null:new TreeMap<>(),"","","");
        if (destify != null)
            destify.accept(broadcast);
        // note - the client expects a strictly monotonic per-user seq, which is wasteful
        // but it doesn't work without them eg PostedData doesn't show new messages notification without it
        // use seq=0 and append the correct one later
        Message msg = new Message(event,obj,broadcast,0);
        for (String omit:omits)
            msg.map.put(omit,true);
        return msg.prep();
    }

    // fixme - omit users needs to be preserved to enable filtering on a per-user basis
    //   this effects all the addUser* andMessage methods
    void addUser(int kuser,Message msg) {
        relayOnly();
        for (EchoSocket echo = session(kuser,null,false); echo != null; echo = echo.next)
            echo.addMessage(msg);
    }
    void addAllUsers(Message msg) {
        relayOnly();
        for (EchoSocket echo : sockets.values())
            echo.addMessage(msg);
    }
    void addTeamUsers(int kteam,ArrayList<Integer> kusers,LinkedList<Message> msgs) {
        addUsers(kusers,msgs);
        teamMessages.remove(kteam);
    }
    void addChanUsers(int kchan,ArrayList<Integer> kusers,LinkedList<Message> msgs) {
        addUsers(kusers,msgs);
        channelMessages.remove(kchan);
    }
    void addUsers(ArrayList<Integer> kusers,LinkedList<Message> msgs) {
        relayOnly();
        for (int kuser : kusers) {
            for (EchoSocket echo = session(kuser,null,false); echo != null; echo = echo.next)
                if (echo != null)
                    for (Message msg : msgs)
                        echo.addMessage(msg);
        }
    }
    
    static <KK,VV> LinkedList<VV> addToMap(TreeMap<KK,LinkedList<VV>> map,KK kchan,VV ... obj) {
        LinkedList<VV> list = map.get(kchan), alloc=null;
        if (list==null)
            map.put(kchan,list = alloc = new LinkedList<>());
        for (VV val : obj)
            list.add(val);
        return alloc;
    }

    static class Others {
        int [] others = new int[0];
        String [] omits = new String[0];
        public Others(int ... others) { this.others = others; }
        public Others(String ... omits) { this.omits = omits; }
    }
    static Others others(int ... others) { return new Others(others); }
    static Others omits(String ... omits) { return new Others(omits); }
    static int [] others(Others others) { return others==null ? new int[0] : others.others; }
    static String [] omits(Others others) { return others==null ? new String[0] : others.omits; }

    // some sort of a list of (user|string[]) pairs
    // some sort of a list of (chan|team,obj) pairs
    // iterate through chan|team pairs
    //   group by chan|team
    //   create event and convert to json string
    //   add to user map for all connected users in the chan|team
    // iterate through user pairs
    //   send messages
    // need backpressure for
    //   outstanding sent messages
    //   user map
    //   channel map


    public void sendChannel(int kchan,String chanid,Object obj,Others others) {
        Message msg = msg(obj,b->b.channelId = chanid,omits(others));
        add(true,() -> addChannel(kchan,msg,others(others)));
    }
    public void sendTeam(int kteam,String teamid,Object obj,Others others) {
        Message msg = msg(obj,b->b.teamId = teamid,omits(others));
        add(true,() -> addTeam(kteam,msg,others(others)));
    }
    public void sendAll(Object obj,Others others) {
        Message msg = msg(obj,null,omits(others));
        // others.others makes no sense in this context since we've already included everyone
        add(true,() -> addAllUsers(msg));
    }
    public void sendUser(int kuser,String userid,Object obj) {
        Message msg = msg(obj,b->b.userId = userid);
        add(true,() -> add(channelDelay,() -> addUser(kuser,msg)));
    }
    
    String userid(List<HttpCookie> cookies,String name) {
        for (HttpCookie cc : cookies)
            if (cc.getName().equals(name))
                return cc.getValue();
        return "";
    }
    
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator((req,resp) -> new EchoSocket());
    }

    static void print(Object...objs) { for (Object obj : objs) System.out.println("ws: " + obj); }
    
    public class EchoSocket implements WebSocketListener, WriteCallback {
        String userid;
        Integer kuser;
        Session session;
        Sessions mmauth;
        AtomicInteger pending = new AtomicInteger();
        LinkedList<String> list = new LinkedList<>();
        int seq;
        EchoSocket next;
        // fixme - should decouple the connection from the user to enable multiple connections per user
        
        public void onWebSocketClose(int statusCode,String reason) {
            add(true,() -> session(kuser,this,true));
        }

        public void onWebSocketConnect(Session $session) {
            session = $session;
            session.setIdleTimeout(0);
            List<HttpCookie> cookies = session.getUpgradeRequest().getCookies();
            // fixme - for web requests, mattermost sometimes uses Authorization: BEARER <token>
            //   should probably check that here too
            String token = userid(cookies,MatterControl.mmauthtoken);
            kilim.Task.spawnCall(() -> {
                // fixme - race condition (very weak)
                // use a loop, addnb and a lock
                db4j.submitCall(txn -> {
                    Integer ksess = dm.sessionMap.find(txn,token);
                    Sessions sess = null;
                    if (ksess != null)
                        sess = dm.sessions.find(txn,ksess);
                    if (sess != null) {
                        mmauth = sess;
                        userid = sess.userId;
                        kuser = dm.idmap.find(txn,userid);
                    }
                }).await();
                if (mmauth==null) {
                    System.out.println("websocket auth failed");
                    session.close();
                    return;
                }
                HelloData hello = new HelloData("3.10.0.3.10.0.e339a439c43b9447a03f6a920b887062.false");
                // sendUser(kuser,userid,hello);
                Message msg = msg(hello,b->b.userId = userid);
                add(0,() -> {
                    session(kuser,this,false);
                    // fixme - may make sense to have a sendImmediate method if doing so makes the client
                    //   more responsive
                    addMessage(msg);
                });
            });
        }

        public void onWebSocketError(Throwable cause) {
            print("error: "+cause);
        }
        void process(Client frame) {
            if (kuser==null) return;
            if (frame.action.equals("user_typing")) {
                String chanid = frame.data.channelId;
                db4j.submit(txn -> dm.status.set(txn,kuser,
                        Tuplator.StatusEnum.online.tuple(false,timestamp())));
                spawnQuery(db4j.submit(txn -> dm.idmap.find(txn,chanid)),
                        kchan -> send.typing(frame.data.parentId,userid,chanid,kchan.val));
            }
        }

        public void onWebSocketText(String message) {
            Client frame = matter.gson.fromJson(message,Client.class);
            process(frame);
            Response reply = new Response("OK",frame.seq);
            String text = matter.gson.toJson(reply);
            add(true,() -> addMessage(text));
        }

        @Override
        public void onWebSocketBinary(byte[] arg0,int arg1,int arg2) {
            /* ignore */
        }

        int runUser() {
            relayOnly();
            int num = 0;
            if (session.isOpen())
                for (; (num=pending.get()) < maxPending && !list.isEmpty(); pending.incrementAndGet()) {
                    String msg = list.poll();
                    session.getRemote().sendString(msg,this);
                }
            return num;
        }
        void addMessage(Message msg) {
            relayOnly();
            if (!msg.omit(userid))
                addMessage(msg.json(seq++));
        }
        void addMessage(String msg) {
            relayOnly();
            boolean start = list.isEmpty() && pending.get()==0;
            list.add(msg);
            if (start)
                addActive(this);
        }
        void isActive() {
            relayOnly();
            if (pending.get()==0 && !list.isEmpty())
                runUser();
        }

        public void writeFailed(Throwable x) {
            writeSuccess();
            System.out.println("matter.ws.fail: "+x.getMessage());
        }
        public void writeSuccess() {
            int cnt = pending.decrementAndGet();
            if (cnt==0)
                add(true,this::isActive);
        }
    }
    public static void main(String[] args) throws Exception {
        MatterFull.main(args);
    }
}
