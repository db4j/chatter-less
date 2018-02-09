package foobar;

import foobar.SpringMatterAuth.MyAuth;
import java.util.Collection;
import java.util.LinkedList;
import kilim.Pausable;
import mm.data.Sessions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class SpringMatterAuth extends SpringDb4j<MyAuth> {
    MatterControl matter;
    MatterData dm;
    MatterWebsocket ws;

    void setup(MatterControl $matter) {
        matter = $matter;
        db4j = matter.db4j;
        dm = matter.dm;
        ws = matter.ws;
    }

    
    
    public static class MyAuth implements Authentication {
        String userid;
        String token;
        String uid;
        Sessions mmauth;
        Integer kauth;

        public MyAuth(String userid,String token) {
            this.userid = userid;
            this.token = token;
        }

        public Collection<? extends GrantedAuthority> getAuthorities() { return new LinkedList(); }
        public Object getCredentials() { return "proof"; }
        public Object getDetails() { return null; }
        public Object getPrincipal() { return this; }
        public boolean isAuthenticated() { return true; }
        public void setAuthenticated(boolean isAuthenticated) {}
        public String getName() { return userid; }
    }
    
    String auth(Txn<MyAuth> txn) throws Pausable {
        MyAuth auth = txn.auth;
        Integer ksess = dm.sessionMap.find(txn,auth.token);
        if (ksess != null)
            auth.mmauth = dm.sessions.find(txn,ksess);
        if (auth.mmauth != null) {
            auth.kauth = ksess;
            auth.uid = auth.mmauth.userId;
        }
        return auth.uid;
    }    

}
