package foobar;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kilim.Mailbox;
import kilim.Pausable;
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

    public class ChannelMessage implements Runnable {
        int kchan;
        Object payload;
        public void run() {
            db4j.submit(txn -> {
                
                return null;
            });
        }
    }
    public static class UserMessage {
        String uid;
        String text;
        public UserMessage() {}
        public UserMessage(String uid,String text) { this.uid = uid; this.text = text; }
    }
    public static class Connect {
        String uid;
        Session outbound;
        public Connect(String $uid,Session $outbound) { uid = $uid; outbound = $outbound; }
    }
    Mailbox mbox = new Mailbox<>();

    void addnb(Runnable obj) { mbox.putnb(obj); }
    void add(Runnable obj) throws Pausable { mbox.put(obj); }
    void addUser(int kuser,String msg) {
        addToMap(userMessages,kuser,msg);
    }

    TreeMap<Integer,LinkedList<Object>> channelMessages = new TreeMap();
    TreeMap<Integer,LinkedList<Object>> teamMessages = new TreeMap();
    TreeMap<Integer,LinkedList<String>> userMessages = new TreeMap();
    ArrayList<Session> sessions = new ArrayList();
    TreeMap<String,Session> sessionMap = new TreeMap<>();
    TreeMap<Integer,PendingUser> pendingUsers = new TreeMap();

    static class PendingUser {
        int kuser;
        LinkedList<String> messages;
    }
    
    Integer lastKchan;
    
    void processChannel() {
        Integer kchan = lastKchan;
        TreeMap<Integer,LinkedList<Object>> map = null;
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
    
    public class RelayTask extends kilim.Task {
        public void execute() throws Pausable,Exception {
            while (true) {
                Object obj = mbox.get();
                if (obj instanceof UserMessage) {
                    UserMessage msg = (UserMessage) obj;
                    Session outbound = sessionMap.get(msg.uid);
                    if (outbound != null && outbound.isOpen())
                        outbound.getRemote().sendString(msg.text,new MyCallback());
                }
                else {
                    Connect conn = (Connect) obj;
                    if (conn.outbound==null) sessionMap.remove(conn.uid);
                    else                     sessionMap.put(conn.uid,conn.outbound);
                }
            }
        }
    }
    RelayTask relay = new RelayTask();
    { relay.start(); }

    public static <QQ extends Db4j.Query> void spawnQuery(QQ query,Spawnable.Call1<QQ> map) {
        kilim.Task.spawn(() -> {
            query.await();
            map.execute(query);
            return null;
        });
    }
    
    public class ChannelTask extends kilim.Task {
        int kchan;
        String chanid;
        public void execute() throws Pausable,Exception {
            db4j.submit(txn -> {
                return null;
            }).await();
            
        }
    }
    
    Message msg(Object obj) {
        Message m = new Message();
        m.event = obj.getClass().getName().replace("Data","").toLowerCase();
        m.data = obj;
        m.broadcast = new Broadcast(null,"","","");
        return m;
    }

    void addUsers(ArrayList<Integer> kusers,LinkedList<Object> msgs) {
        String [] sms = new String[msgs.size()];
        for (int ii=0; ii < sms.length; ii++)
            sms[ii] = matter.gson.toJson(msgs.poll());
        for (int kuser : kusers)
            addToMap(userMessages,kuser,sms);
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

    public void send(String msg,ArrayList<Integer> kusers) {
        
        
    }
    
    public class MyCallback implements WriteCallback {
        public void writeFailed(Throwable x) {
        }
        public void writeSuccess() {
        }
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
    
    public class EchoSocket implements WebSocketListener {
        String userid;

        public void onWebSocketClose(int statusCode,String reason) {
            mbox.putnb(new Connect(userid,null));
        }

        public void onWebSocketConnect(Session session) {
            List<HttpCookie> cookies = session.getUpgradeRequest().getCookies();
            userid = userid(cookies,MatterLess.mmuserid);
            mbox.putnb(new Connect(userid,session));
        }

        public void onWebSocketError(Throwable cause) {
            print("error: "+cause);
        }

        public void onWebSocketText(String message) {
            Client frame = matter.gson.fromJson(message,Client.class);
            Response reply = new Response("OK",frame.seq);
            String text = matter.gson.toJson(reply);
            mbox.putnb(new UserMessage(userid,text));
        }

        @Override
        public void onWebSocketBinary(byte[] arg0,int arg1,int arg2) {
            /* ignore */
        }
    }
}
