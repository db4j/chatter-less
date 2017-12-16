package foobar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import mm.data.Users;
import mm.rest.ChannelsxMembersReps;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;
import org.srlutils.Util;
import static foobar.Utilmm.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;

public class MatterControl {
    static Gson pretty = new GsonBuilder().setPrettyPrinting().create();
    static Gson gson = new GsonBuilder().registerTypeAdapter(String.class, new PointAdapter()).create();
    static Gson nullGson = new GsonBuilder().serializeNulls().create();
    static Gson skipGson = new GsonBuilder().create();
    static JsonParser parser = new JsonParser();
    
    MatterData dm = new MatterData(this);
    Db4j db4j = dm.start("./db_files/hunk.mmap",false);
    MatterWebsocket ws = new MatterWebsocket(this);
    MatterKilim mk = new MatterKilim();
    { mk.setup(this); }
    
    public static class NickInfo {
        int kuser;
        String userid;
        public NickInfo(int kuser,String userid) { this.kuser = kuser; this.userid = userid; }

        public boolean equals(Object obj) {
            return ((NickInfo) obj).kuser==kuser;
        }
        
    }
    // username -> (kuser,userid)
    ConcurrentHashMap<String,ConcurrentLinkedQueue<NickInfo>> mentionMap = new ConcurrentHashMap<>();
    {
        dm.users.getall().forEach(pair -> addNicks(pair.val,pair.key));
    }
    // fixme - should be immutable
    ConcurrentLinkedQueue<NickInfo> dummyNicks = new ConcurrentLinkedQueue();
    <TT> TT either(TT val,TT fallback) { return val==null ? fallback:val; }
    ConcurrentLinkedQueue<NickInfo> getNicks(String key) { return either(mentionMap.get(key),dummyNicks); }
    void addNicks(Users user,int kuser) {
        NickInfo row = new NickInfo(kuser,user.id);
        for (String nick : getUserNicks(user))
            addNick(nick,row);
    }
    void addNick(String nick,NickInfo row) {
        ConcurrentLinkedQueue<NickInfo> row2 = new ConcurrentLinkedQueue<>(), prev;
        row2.add(row);
        prev = mentionMap.putIfAbsent(nick,row2);
        if (prev != null) prev.add(row);
    }
    void delNick(String nick,int kuser) {
        ConcurrentLinkedQueue<NickInfo> prev = mentionMap.get(nick);
        prev.remove(new NickInfo(kuser,null));
        // fixme - no easy way to delete empty queues, so they "leak"
    }
    ArrayList<NickInfo> getMentions(String text,Collection<String> tags) {
        ArrayList<NickInfo> list = new ArrayList<>();
        Matcher mat = Regexen.mention.matcher(text);
        while (mat.find()) {
            String name = mat.group(1);
            boolean yes = name.charAt(0)=='@';
            for (NickInfo nickinfo : getNicks(name)) {
                yes = true;
                list.add(nickinfo);
            }
            if (yes) tags.add(name);
        }
        return list;
    }
    
    /*
    
    mentions:
    store list of mention words in both the database and mirror them in an in-memory data structure
      on database startup rebuild the mirror
      mention words never get deleted (could do it at startup if abused)
    scan message -> matching mention words, ie kmention
      for each one, need to find channel users matching that kmention
      for @username: addExact to postsIndex
      non-channel member @username for a user on the team is special
        show an ephemeral message
    kmention + need to know if it's an @username or not
    need to map (kchannel,kmention) -> kuser
      can either maintain exactly that mapping
      or maintain kmention -> kuser, and then check if each user is a member of the channel
    
    
    
    
    
    */

    String format(Users user) {
        return user.username + ":" + user.id;
    }
    
    void printUsers()
    {
        db4j.submitCall(txn -> {
            System.out.println(
            print(dm.users.getall(txn).vals(), x->format(x)));
            System.out.println(
            print(dm.idmap.getall(txn).keys(), x->x));
        });
    }

    static <TT> String print(List<TT> vals,Function<TT,String> mapping) {
        return vals.stream().map(mapping).collect(Collectors.joining("\n")); }
    
    static String ugly(Object obj) { return gson.toJson(obj); }
    static String pretty(Object obj) { return pretty.toJson(obj); }
    static String pretty(String json) { return pretty(parser.parse(json).getAsJsonObject()); }
    static void print(Object obj) { System.out.println(pretty(obj)); }

    public static class PointAdapter extends TypeAdapter<String> {
        public String read(JsonReader reader) throws IOException {
            return reader.nextString();
        }

        public void write(JsonWriter writer,String value) throws IOException {
            writer.value(value==null ? "":value);
        }
    }
    

    
    public static <TT extends Query> void chain(TT query,Consumer<TT> cb) {
        kilim.Task.spawnCall(() -> {
            query.await();
            cb.accept(query);
        });
    }
    
    static <TT> TT set(TT val,Consumer<TT> ... consumers) {
        for (Consumer sumer : consumers)
            sumer.accept(val);
        return val;
    }


    // string literals in gson    
    // notify props
    // https://github.com/google/gson/issues/326
    
    static String salt(String plain) { return plain; }
    static String mmuserid = "MMUSERID";
    static String mmauthtoken = "MMAUTHTOKEN";
    static String fortyZeros = "0000000000000000000000000000000000000000";

    static String sha1hex(String ... vals) {
        MessageDigest md = tryval(() -> MessageDigest.getInstance("SHA-1"));
        int num = vals.length;
        for (int ii=0; ii < num; ii++) {
            byte [] content = vals[ii].getBytes(StandardCharsets.US_ASCII);
            md.update(content);
        }
        byte [] hash = md.digest();
        String result = new BigInteger(1,hash).toString(16);
        int len = result.length();
        if (len < 40)
            result = fortyZeros.substring(0,40-len) + result;
        return result;
    }

    static void tryrun(Exceptional runner) {
        try { runner.run(); }
        catch (RuntimeException ex) { throw ex; }
        catch (Throwable ex) { throw new RuntimeException(ex); }
    }
    static <TT> TT tryval(ValueExceptional<TT> runner) {
        try { return runner.run(); }
        catch (RuntimeException ex) { throw ex; }
        catch (Throwable ex) { throw new RuntimeException(ex); }
    }
    interface Exceptional {
        void run() throws Throwable;
    }
    interface ValueExceptional<TT> {
        TT run() throws Throwable;
    }
    

    // example of using gson with an embedded (string) literal
    public static void demoGsonLiteral() {
        ChannelsxMembersReps reps = new ChannelsxMembersReps();
        String literal = "{desktop: \"default\", email: \"default\", mark_unread: \"all\", push: \"default\"}";
        reps.notifyProps = parser.parse(literal);
        System.out.println("json: " + pretty.toJson(reps));
    }

    SessionMaker sessioner = new SessionMaker();
    private static MessageDigest shax(String alg) {
        try {
            return java.security.MessageDigest.getInstance( alg );
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new RuntimeException( "request for " + alg + " message digest failed", ex );
        }
    }
    // imported from the chickenSoup diary
    public static class SessionMaker {
        static int nsalt = 8, nhash = 32;

        public SecureRandom srand = new SecureRandom();
        public MessageDigest sha2 = shax("SHA-256");
        
        public byte [] digest(byte [] raw,byte [] salt) {
            // digest is [salt,hash]
            if (salt==null) srand.nextBytes(salt = new byte[nsalt]);
            sha2.reset();
            sha2.update(salt,0,nsalt);
            sha2.update(raw);
            byte [] hash = sha2.digest();
            byte [] digest = new byte[nsalt+nhash];
            Util.dup(salt,0,nsalt,digest,    0);
            Util.dup(hash,0,nhash,digest,nsalt);
            return digest;
        }
    }
    
    
   public static void main(String [] args) {
       int num = 3;
       String [] vals = new String[num];
       for (int ii=0; ii < num; ii++) vals[ii] = newid();
       String hash = sha1hex(vals);
       System.out.println(hash);
   }
    
    
}
