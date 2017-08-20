package foobar;

import org.db4j.Db4j;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
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
    
    public Object createWebSocket(ServletUpgradeRequest req,ServletUpgradeResponse resp) {
        return new EchoSocket();
    }
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(this);
    }

    static void print(Object...objs) { for (Object obj : objs) System.out.println("ws: " + obj); }
    
    public class EchoSocket implements WebSocketListener {
        private Session outbound;

        public void onWebSocketClose(int statusCode,String reason) {
            this.outbound = null;
        }

        public void onWebSocketConnect(Session session) {
            this.outbound = session;
//            this.outbound.getRemote().sendString("You are now connected to "+this.getClass().getName(),null);
        }

        public void onWebSocketError(Throwable cause) {
            print("WebSocket Error",cause);
        }

        public void onWebSocketText(String message) {
            if ((outbound!=null)&&(outbound.isOpen())) {
                print("Echoing back text message [{}]",message,matter.newid());
//                outbound.getRemote().sendString(message,null);
            }
        }

        @Override
        public void onWebSocketBinary(byte[] arg0,int arg1,int arg2) {
            /* ignore */
        }
    }
}
