package foobar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import mm.data.Users;
import mm.rest.ChannelsxMembersReps;
import mm.rest.TeamsReps;
import org.db4j.Db4j;
import org.db4j.Db4j.Query;

public class MatterControl {
    static Gson pretty = new GsonBuilder().setPrettyPrinting().create();
    static Gson gson;
    static {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(String.class, new PointAdapter());
        gson = builder.create();
        TeamsReps tr = new TeamsReps();
        tr.id = "hello\"world";
        String txt = gson.toJson(tr);
        System.out.println(txt);
    }
    static JsonParser parser = new JsonParser();
    
    MatterData dm = new MatterData();
    Db4j db4j = dm.start("./db_files/hunk.mmap",false);

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
    kilim.http.HttpServer kilimServer;
    MatterWebsocket ws = new MatterWebsocket(this);

    static <TT> String print(List<TT> vals,Function<TT,String> mapping) {
        return vals.stream().map(mapping).collect(Collectors.joining("\n")); }
    
    MatterControl() throws Exception {
        kilimServer = new kilim.http.HttpServer(9091,
                () -> set(new MatterKilim(),x->x.setup(this)));
    }
    
    
    static String ugly(Object obj) { return gson.toJson(obj).toString(); }
    static String pretty(Object obj) { return pretty.toJson(obj).toString(); }
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

    

    SecureRandom random = new SecureRandom();
    String newid() {
        String val = "";
        while (val.length() != 26)
            val = new BigInteger(134,random).toString(36);
        System.out.println("newid: " + val);
        return val;
    }


    public static void main(String [] args) {
        ChannelsxMembersReps reps = new ChannelsxMembersReps();
        String literal = "{desktop: \"default\", email: \"default\", mark_unread: \"all\", push: \"default\"}";
        reps.notifyProps = parser.parse(literal);
        System.out.println("json: " + pretty.toJson(reps));
    }
    
}
