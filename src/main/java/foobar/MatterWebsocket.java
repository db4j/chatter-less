package foobar;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import kilim.Mailbox;
import kilim.Pausable;
import mm.ws.client.Client;
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

    public static class Message {
        String uid;
        String text;
        public Message() {}
        public Message(String uid,String text) { this.uid = uid; this.text = text; }
    }
    public static class Connect {
        String uid;
        Session outbound;
        public Connect(String $uid,Session $outbound) { uid = $uid; outbound = $outbound; }
    }
    Mailbox mbox = new Mailbox<>();
    
    public class RelayTask extends kilim.Task {
        public void execute() throws Pausable,Exception {
            while (true) {
                Object obj = mbox.get();
                if (obj instanceof Message) {
                    Message msg = (Message) obj;
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

    TreeMap<String,Session> sessionMap = new TreeMap<>();

    public class ChannelTask extends kilim.Task {
        int kchan;
        String chanid;
        public void execute() throws Pausable,Exception {
            db4j.submit(txn -> {
                return null;
            });
        }
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
    public void sendChannel(int kchan,Object obj) {
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
            mbox.putnb(new Message(userid,text));
        }

        @Override
        public void onWebSocketBinary(byte[] arg0,int arg1,int arg2) {
            /* ignore */
        }
    }
}
