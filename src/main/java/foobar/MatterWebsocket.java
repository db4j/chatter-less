package foobar;

import static foobar.MatterControl.gson;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Spawnable;
import kilim.Task;
import mm.rest.Xxx;
import mm.ws.client.Client;
import mm.ws.server.AddedToTeamData;
import mm.ws.server.Broadcast;
import mm.ws.server.HelloData;
import mm.ws.server.LeaveTeamData;
import mm.ws.server.Message;
import mm.ws.server.PostEditedData;
import mm.ws.server.PostedData;
import mm.ws.server.Response;
import mm.ws.server.TypingData;
import mm.ws.server.UserAddedData;
import mm.ws.server.UserRemovedData;
import mm.ws.server.UserUpdatedData;
import org.db4j.Db4j;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.srlutils.Simple;

public class MatterWebsocket extends WebSocketServlet implements WebSocketCreator {
    MatterControl matter;
    MatterData dm;
    MatterKilim mk;
    Db4j db4j;
    
    MatterWebsocket(MatterControl $matter) {
        matter = $matter;
        dm = matter.dm;
        mk = new MatterKilim();
        mk.setup(matter);
        db4j = matter.db4j;
    }

    Mailbox<Relayable> mbox = new Mailbox<>();
    int numBoxFail = 0;

    interface Relayable {
        void run();
    }
    /**
     * attempt to send the runnable to the relay.
     * note: the name is overloaded to allow find usages for all mbox access
     * @param dummy
     * @param obj 
     */
    boolean add(boolean dummy,Relayable obj) {
        boolean success = mbox.putnb(obj);
        if (dummy && !success) numBoxFail++;
        return success;
    }
    void add(int delay,Relayable obj) throws Pausable {
        if (delay > 0) Task.sleep(delay);
        mbox.put(obj);
    }

    // chan and team need to be distinct because kchan and kteams are not orthogonal
    TreeMap<Integer,LinkedList<String>> channelMessages = new TreeMap();
    TreeMap<Integer,LinkedList<String>> teamMessages = new TreeMap();
    HashMap<Integer,EchoSocket> sockets = new HashMap<>();
    EchoSocket [] active;
    int nactive;
    { growActive(); }
    
    static int perActive = 1024;
    
    void growActive() {
        active = new EchoSocket[perActive];
        nactive = 0;
    }

    EchoSocket session(int kuser,EchoSocket session,boolean remove) {
        relayOnly();
        if (remove) return sockets.remove(kuser);
        else if (session==null) return sockets.get(kuser);
        else return sockets.put(kuser,session);
    }

    int teamDelay = 500;
    int channelDelay = 500;
    int usersDelay = 500;
    
    void addChannel(int kchan,String text) {
        relayOnly();
        LinkedList<String> alloc = addToMap(channelMessages,kchan,text);
        if (alloc != null) 
            spawnQuery(db4j.submit(txn ->
                        dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true)).getall(x -> x.key.v2)),
                query ->
                    add(channelDelay,() -> addChanUsers(kchan,query.val,alloc)));
    }    
    void addTeam(int kteam,String text,Integer ... others) {
        relayOnly();
        LinkedList<String> alloc = addToMap(teamMessages,kteam,text);
        if (alloc != null) 
            spawnQuery(db4j.submit(txn ->
                        dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true)).getall(x -> x.key.v2)),
                query -> {
                    for (Integer other : others)
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
    
    String decamelify(String text) {
        Matcher mat = Pattern.compile("(?<=[a-z])[A-Z]").matcher(text);
        StringBuffer buf = new StringBuffer();
        while (mat.find())
            mat.appendReplacement(buf, "_"+mat.group());
        mat.appendTail(buf);
        return buf.toString();
    }


    public Send send = new Send();
    public class Send {
        public void userAdded(String teamId,String userId,String channelId,Integer kchan) {
            UserAddedData brief = new UserAddedData(teamId,userId);
            sendChannel(kchan,channelId,brief);
        }
        public void userRemoved(String removerId,String userId,String channelId,Integer kchan) {
            UserRemovedData brief = new UserRemovedData(channelId,removerId,userId);
            sendChannel(kchan,channelId,brief);
        }
        public void postEdited(Xxx reply,String chanid,Integer kchan) {
            String text = gson.toJson(reply);
            PostEditedData brief = new PostEditedData(text);
            sendChannel(kchan,chanid,brief);
        }
        public void posted(Xxx reply,mm.data.Channels chan,String username,Integer kchan) {
            // fixme - calculate mentions
            String mentions = null;
            String text = gson.toJson(reply);
            PostedData brief = new PostedData(chan.displayName,chan.name,chan.type,text,username,chan.teamId,mentions);
            sendChannel(kchan,chan.id,brief);
        }
        public void userUpdated(mm.rest.User user,String chanid,Integer kchan) {
            UserUpdatedData brief = new UserUpdatedData(user);
            sendChannel(kchan,chanid,brief);
        }
        public void addedToTeam(int kuser,String teamId, String userId) {
            AddedToTeamData brief = new AddedToTeamData(teamId,userId);
            sendUser(kuser,userId,brief);
        }
        public void leaveTeam(String userId,String teamId,Integer kteam,Integer kuser) {
            LeaveTeamData brief = new LeaveTeamData(teamId,userId);
            sendTeam(kteam,teamId,brief,kuser);
        }

    }
    
    
    Message msg(Object obj,String ... omits) {
        Message m = new Message();
        String klass = obj.getClass().getSimpleName().replace("Data","");
        m.event = decamelify(klass).toLowerCase();
        m.data = obj;
        TreeMap<String,Boolean> map = omits.length==0 ? null:new TreeMap<>();
        for (String omit:omits)
            map.put(omit,true);
        m.broadcast = new Broadcast(map,"","","");
        // fixme - the client expects a strictly monotonic per-user seq
        // but doing so means either serializing per-user or string replacing
        // plus it duplicates all those strings, limiting scale
        // for now, just use a fixed seq and hope the client doesn't barf
        m.seq = 0;
        return m;
    }

    void addUser(int kuser,String msg) {
        relayOnly();
        EchoSocket echo = session(kuser,null,false);
        if (echo != null)
            echo.addMessage(msg);
    }
    void addTeamUsers(int kteam,ArrayList<Integer> kusers,LinkedList<String> msgs) {
        addUsers(kusers,msgs);
        teamMessages.remove(kteam);
    }
    void addChanUsers(int kchan,ArrayList<Integer> kusers,LinkedList<String> msgs) {
        addUsers(kusers,msgs);
        channelMessages.remove(kchan);
    }
    void addUsers(ArrayList<Integer> kusers,LinkedList<String> msgs) {
        relayOnly();
        for (int kuser : kusers) {
            EchoSocket echo = session(kuser,null,false);
            if (echo != null)
                for (String msg : msgs)
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
    public void sendChannel(int kchan,String chanid,Object obj) {
        Message msg = msg(obj);
        msg.broadcast.channelId = chanid;
        String text = matter.gson.toJson(msg);
        add(true,() -> addChannel(kchan,text));
    }
    public void sendTeam(int kteam,String teamid,Object obj,Integer ... others) {
        Message msg = msg(obj);
        msg.broadcast.teamId = teamid;
        String text = matter.gson.toJson(msg);
        add(true,() -> addTeam(kteam,text,others));
    }
    public void sendUser(int kuser,String userid,Object obj) {
        Message msg = msg(obj);
        msg.broadcast.userId = userid;
        String text = matter.gson.toJson(msg);
        add(true,() -> addUser(kuser,text));
    }
    
    String userid(List<HttpCookie> cookies,String name) {
        for (HttpCookie cc : cookies)
            if (cc.getName().equals(name))
                return cc.getValue();
        return "";
    }
    
    public Object createWebSocket(ServletUpgradeRequest req,ServletUpgradeResponse resp) {
        return new EchoSocket();
    }
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(this);
    }

    static void print(Object...objs) { for (Object obj : objs) System.out.println("ws: " + obj); }
    
    public class EchoSocket implements WebSocketListener, WriteCallback {
        String userid;
        Integer kuser;
        Session session;
        AtomicInteger pending = new AtomicInteger();
        LinkedList<String> list = new LinkedList<>();
        // fixme - should decouple the connection from the user to enable multiple connections per user
        
        public void onWebSocketClose(int statusCode,String reason) {
            add(true,() -> session(kuser,null,true));
        }

        public void onWebSocketConnect(Session $session) {
            session = $session;
            session.setIdleTimeout(0);
            List<HttpCookie> cookies = session.getUpgradeRequest().getCookies();
            userid = userid(cookies,MatterControl.mmuserid);
            kilim.Task.spawnCall(() -> {
                // fixme - race condition (very weak)
                // use a loop, addnb and a lock
                kuser = mk.get(dm.idmap,userid);
                HelloData hello = new HelloData("3.10.0.3.10.0.e339a439c43b9447a03f6a920b887062.false");
                Message msg = msg(hello);
                msg.broadcast.userId = userid;
                String text = matter.gson.toJson(msg);
                add(0,() -> {
                    session(kuser,this,false);
                    // fixme - may make sense to have a sendImmediate method if doing so makes the client
                    //   more responsive
                    addMessage(text);
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
                        Tuplator.StatusEnum.online.tuple(false,MatterKilim.timestamp())));
                Message msg = msg(new TypingData(frame.data.parentId,userid),userid);
                spawnQuery(db4j.submit(txn -> dm.idmap.find(txn,chanid)),
                        query -> sendChannel(query.val,chanid,msg));
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
                for (; (num=pending.get()) < maxPending && !list.isEmpty(); pending.incrementAndGet())
                    session.getRemote().sendString(list.poll(),this);
            return num;
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
