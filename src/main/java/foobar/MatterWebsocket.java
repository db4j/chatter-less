package foobar;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Spawnable;
import mm.ws.client.Client;
import mm.ws.server.Broadcast;
import mm.ws.server.Message;
import mm.ws.server.Response;
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
    MatterLess matter;
    MatterData dm;
    MatterKilim mk;
    Db4j db4j;
    
    MatterWebsocket(MatterLess $matter) {
        matter = $matter;
        dm = matter.dm;
        mk = new MatterKilim();
        mk.setup(matter);
        db4j = matter.db4j;
    }

    Mailbox<Runnable> mbox = new Mailbox<>();

    void addnb(Runnable obj) { mbox.putnb(obj); }
    void add(Runnable obj) throws Pausable { mbox.put(obj); }
    void addUser(int kuser,String msg) {
        relayOnly();
        // fixme - should we be defensive with length ???
        EchoSocket echo = session(kuser,null,false);
        if (echo != null) echo.addMessage(msg);
    }

    TreeMap<Integer,LinkedList<Object>> channelMessages = new TreeMap();
    TreeMap<Integer,LinkedList<Object>> teamMessages = new TreeMap();
    TreeMap<Integer,LinkedList<String>> userMessages = new TreeMap();
    ArrayList<EchoSocket> sockets = new ArrayList();
    LinkedList<int []> active = new LinkedList();
    int kactive;
    int nactive;
    { growActive(); }
    
    static int perActive = 1024;
    
    void growActive() {
        active.addLast(new int[perActive]);
        nactive = 0;
    }

    EchoSocket session(int kuser,EchoSocket session,boolean remove) {
        return setArray(sockets,session != null|remove,kuser,session);
    }
    
    
    Integer lastKchan;
    Integer kuser0;
    
    void processChannel() {
        Integer kchan = lastKchan;
        TreeMap<Integer,LinkedList<Object>> map = channelMessages;
        Map.Entry<Integer,LinkedList<Object>> entry = kchan==null ? map.firstEntry() : map.higherEntry(kchan);
        if (entry==null)
            lastKchan = null;
        else {
            lastKchan = entry.getKey();
            spawnQuery(
                db4j.submit(txn ->
                        dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,0)).getall(x -> x.key.v1)),
                query ->
                    add(() -> addUsers(query.val,entry.getValue())));
        }
    }
    
    int maxPending = 5;

    // false adds are allowed so long as they're fairly rare (even under load)
    // a failure to add is more severe
    Integer addActive(int kuser,boolean pop) {
        relayOnly();
        if (pop) {
            int [] first = active.getFirst(), last = active.getLast();
            if (first==last & kactive==nactive)
                return null;
            int val = active.getFirst()[kactive++];
            if (kactive==perActive) {
                kactive = 0;
                if (first==last) nactive = 0;
                else active.removeFirst();
            }
            return val;
        }
        if (nactive==perActive) growActive();
        active.getLast()[nactive++] = kuser;
        return null;
    }
    
    void runUser() {
        Integer kuser = addActive(0,true);
        EchoSocket echo = session(kuser,null,false);
        if (echo != null) echo.runUser();
    }
    
    public class RelayTask extends kilim.Task {
        Thread thread;
        public void execute() throws Pausable,Exception {
            thread = Thread.currentThread();
            while (true) {
                Runnable runnee = mbox.getnb();
                if (runnee==null)
                    runUser();
                else
                    runnee.run();
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
    
    Message msg(Object obj) {
        Message m = new Message();
        m.event = obj.getClass().getName().replace("Data","").toLowerCase();
        m.data = obj;
        m.broadcast = new Broadcast(null,"","","");
        return m;
    }

    void addUsers(ArrayList<Integer> kusers,LinkedList<Object> msgs) {
        relayOnly();
        String [] sms = new String[msgs.size()];
        for (int ii=0; ii < sms.length; ii++)
            sms[ii] = matter.gson.toJson(msgs.poll());
        for (int kuser : kusers) {
            EchoSocket echo = session(kuser,null,false);
            if (echo != null)
                for (String text : sms)
                    echo.addMessage(text);
        }
    }
    
    static <KK,VV> void addToMap(TreeMap<KK,LinkedList<VV>> map,KK kchan,VV ... obj) {
        LinkedList<VV> list = map.get(kchan);
        if (list==null)
            map.put(kchan,list = new LinkedList<>());
        for (VV val : obj)
            list.add(val);
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
        addnb(() -> addToMap(channelMessages,kchan,text));
    }

    public void send(String msg,Integer ... kusers) {
        addnb(() -> {
            for (Integer kuser : kusers)
                addUser(kuser,msg);
        });
        
    }
    public void send(String msg,ArrayList<Integer> kusers) {
        
        
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

    static <TT> TT setArray(ArrayList<TT> array,boolean set,int index,TT value) {
        int size = array.size();
        if (index >= size)
            if (!set) return null;
            else array.ensureCapacity(index+1);
        return set ? array.set(index,value) : array.get(index);
    }
    static void print(Object...objs) { for (Object obj : objs) System.out.println("ws: " + obj); }
    
    public class EchoSocket implements WebSocketListener, WriteCallback {
        String userid;
        Integer kuser;
        Session session;
        AtomicInteger pending;
        LinkedList<String> list = new LinkedList<>();
        
        public void onWebSocketClose(int statusCode,String reason) {
            addnb(() -> session(kuser,null,true));
        }

        public void onWebSocketConnect(Session $session) {
            session = $session;
            List<HttpCookie> cookies = session.getUpgradeRequest().getCookies();
            userid = userid(cookies,MatterLess.mmuserid);
            kilim.Task.spawnCall(() -> {
                // fixme - race condition (very weak)
                // use a loop, addnb and a lock
                kuser = mk.get(dm.idmap,userid);
                add(() -> session(kuser,this,false));
            });
        }

        public void onWebSocketError(Throwable cause) {
            print("error: "+cause);
        }

        public void onWebSocketText(String message) {
            Client frame = matter.gson.fromJson(message,Client.class);
            Response reply = new Response("OK",frame.seq);
            String text = matter.gson.toJson(reply);
            send(text,kuser);
        }

        @Override
        public void onWebSocketBinary(byte[] arg0,int arg1,int arg2) {
            /* ignore */
        }

        void runUser() {
            relayOnly();
            if (pending.get() > 0)
                return;
            if (session.isOpen())
                for (; pending.get() < maxPending && !list.isEmpty(); pending.incrementAndGet())
                    session.getRemote().sendString(list.poll(),this);
        }
        void addMessage(String msg) {
            relayOnly();
            if (list.isEmpty() && pending.get()==0)
                addActive(kuser,false);
            list.add(msg);
        }
        void isActive() {
            if (pending.get()==0 && !list.isEmpty())
                addActive(kuser,false);
        }

        public void writeFailed(Throwable x) {
            writeSuccess();
            System.out.println("matter.ws.fail: "+x.getMessage());
        }
        public void writeSuccess() {
            int cnt = pending.decrementAndGet();
            if (cnt==0)
                addnb(this::isActive);
        }
    }
}
