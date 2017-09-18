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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import mm.data.Users;
import mm.rest.ChannelsxMembersReps;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;

public class MatterControl {
    static Gson pretty = new GsonBuilder().setPrettyPrinting().create();
    static Gson gson = new GsonBuilder().registerTypeAdapter(String.class, new PointAdapter()).create();
    static Gson nullGson = new GsonBuilder().serializeNulls().create();
    static Gson skipGson = new GsonBuilder().create();
    static JsonParser parser = new JsonParser();
    
    MatterData dm = new MatterData();
    Db4j db4j = dm.start("./db_files/hunk.mmap",false);
    MatterWebsocket ws = new MatterWebsocket(this);
    MatterKilim mk = new MatterKilim();
    { mk.setup(this); }

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
    
    static SecureRandom random = new SecureRandom();
    static String newid() {
        String val = "";
        while (val.length() != 26)
            val = new BigInteger(134,random).toString(36);
        System.out.println("newid: " + val);
        return val;
    }
    static <TT> TT [] append(TT [] src,TT ... other) {
        TT[] dst = org.srlutils.Util.dup(src,0,src.length+other.length);
        org.srlutils.Util.dup(other,0,other.length,dst,src.length);
        return dst;
    }


    // example of using gson with an embedded (string) literal
    public static void demoGsonLiteral() {
        ChannelsxMembersReps reps = new ChannelsxMembersReps();
        String literal = "{desktop: \"default\", email: \"default\", mark_unread: \"all\", push: \"default\"}";
        reps.notifyProps = parser.parse(literal);
        System.out.println("json: " + pretty.toJson(reps));
    }

   public static void main(String [] args) {
       int num = 3;
       String [] vals = new String[num];
       for (int ii=0; ii < num; ii++) vals[ii] = newid();
       String hash = sha1hex(vals);
       System.out.println(hash);
   }
    
    
}
