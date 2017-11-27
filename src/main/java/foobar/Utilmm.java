package foobar;

import static foobar.MatterControl.gson;
import static foobar.MatterControl.set;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kilim.Pausable;
import kilim.Task;
import kilim.http.HttpResponse;
import mm.data.Users;
import mm.rest.NotifyUsers;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Db4j;
import org.srlutils.Simple;

public class Utilmm {
    static String expires(Object val) {
        if (val instanceof String)
            return (String) val;
        Double days = (double) val;
        Date expdate = new Date();
        long ticks = expdate.getTime() + (long)(days*24*3600*1000);
        expdate.setTime(ticks);
        DateFormat df = new SimpleDateFormat("dd MMM yyyy kk:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        String cookieExpire = "expires=" + df.format(expdate);
        return cookieExpire;
    }

    /**
     * format a cookie and add it to the response
     * @param resp the response to add the cookie to
     * @param name the name of the cookie
     * @param value the value
     * @param days either a double, in which case the number of days till expiration, or a string literal to insert
     * @param httponly whether the cookie should be set httponly
     */
    static void setCookie(HttpResponse resp,String name,String value,Object days,boolean httponly) {
        String expires = expires(days);
        String newcookie = String.format("%s=%s; Path=/; %s",name,value,expires);
        if (httponly) newcookie += "; HttpOnly";
        System.out.println("Set-Cookie: "+newcookie);
        resp.addField("Set-Cookie",newcookie);
    }
    
    static String parse(String sub,String name) {
        return sub.startsWith(name) ? sub.substring(name.length()) : null;
    }

    static String [] getCookies(String cookie,String ... names) {
        boolean dbg = false;
        String [] vals = new String[names.length];
        if (cookie != null && !cookie.isEmpty()) {
            String [] lines = cookie.split("; *");
            for (int ii = 0; ii < lines.length; ii++) {
                String sub = lines[ii];
                if (dbg) System.out.println(sub);
                if (ii+1 < lines.length && lines[ii+1].startsWith("Expires=")) {
                    if (dbg) System.out.println(lines[ii+1]);
                    ii++;
                }
                String part;
                for (int jj=0; jj < names.length; jj++)
                    if ((part=parse(sub,names[jj])) != null)
                        vals[jj] = part;
            }
        }
        return vals;
    }

    static class Regexen {
        // https://docs.oracle.com/javase/tutorial/essential/regex/unicode.html
        // https://www.regular-expressions.info/posixbrackets.html
        // https://www.regular-expressions.info/unicode.html
        static Pattern hashtag = Pattern.compile("\\B#(\\p{L}[\\w-_.]{1,}\\w)");
        static Pattern mention = Pattern.compile("(?:\\B@|\\b)(\\w+)");
    }

    /**
     * extract hashtags from text, add them to tags and then return the space-join of tags
     * @param text the string to scan
     * @param tags the list to add to and to join, ie it should probably be empty initially
     * @return the join of tags with a space as separator
     */
    static String getHashtags(String text,Collection<String> tags) {
        Matcher mat = Regexen.hashtag.matcher(text);
        while (mat.find())
            tags.add(mat.group(0));
        return String.join(" ",tags);
    }

    static ArrayList<String> getUserNicks(Users user) { return getUserNicks(user,new ArrayList()); }
    static ArrayList<String> getUserNicks(Users user,ArrayList<String> list) {
        if (user.notifyProps==null)
            return set(list, x -> { x.add(user.username); x.add("@"+user.username); });
        NotifyUsers notify = gson.fromJson(user.notifyProps,NotifyUsers.class);
        list.add("@"+user.username);
        if (notify.firstName & user.firstName != null && user.firstName.length() > 0)
            list.add(user.firstName);
        for (String key : notify.mentionKeys.split(","))
            list.add(key);
        return list;
    }
    static ArrayList<Integer> getall(Db4j.Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0)).getall(cc -> cc.val);
    }
    static Btree.Range<Btrees.II.Data> prefix(Db4j.Transaction txn,Btrees.II map,int key) throws Pausable {
        return map.findPrefix(map.context().set(txn).set(key,0));
    }
    
    static class Spawner<TT> {
        public Spawner() {}
        public Spawner(boolean $skipNulls) { skipNulls = $skipNulls; }
        boolean skipNulls = true;
        ArrayList<Task.Spawn<TT>> tasks = new ArrayList();
        Task.Spawn<TT> spawn(kilim.Spawnable<TT> body) {
            Task.Spawn task = Task.spawn(body);
            tasks.add(task);
            return task;
        }
        ArrayList<TT> join() throws Pausable {
            ArrayList<TT> vals = new ArrayList();
            for (Task.Spawn<TT> task : tasks) {
                TT val = task.mb.get();
                if (val != Task.Spawn.nullValue) vals.add(val);
                else if (! skipNulls)            vals.add(null);
            }
            return vals;
        }
    }
    /**
     * filter a list, wrapping the selected items in a row, ie an index-value tuple
     * @param <TT> the type of the list
     * @param list the list
     * @param filter the filter to apply, return true to select the item
     * @return the selected indices and items
     */
    static <TT> ArrayList<MatterData.Row<TT>> filterRows(java.util.Collection<TT> list,Function<TT,Boolean> filter) {
        ArrayList<MatterData.Row<TT>> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(item))
                result.add(new MatterData.Row(ii,item));
            ii++;
        }
        return result;
    }
    static <TT> ArrayList<TT> filter(List<TT> list,int first,int num) {
        int last = Math.min(first+num,list.size());
        return new ArrayList<>(list.subList(first,last));
    }
    static <TT> ArrayList<TT> filter(java.util.Collection<TT> list,Function<TT,Boolean> filter) {
        ArrayList<TT> result = new ArrayList<>();
        for (TT item : list) {
            if (filter.apply(item))
                result.add(item);
        }
        return result;
    }
    static <TT,UU> ArrayList<UU> map(java.util.Collection<TT> list,Function<TT,UU> filter) {
        ArrayList<UU> result = new ArrayList<>();
        for (TT item : list)
            result.add(filter.apply(item));
        return result;
    }
    static <TT> ArrayList<TT> filter2(java.util.Collection<TT> list,BiFunction<Integer,TT,Boolean> filter) {
        ArrayList<TT> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(ii++,item))
                result.add(item);
        }
        return result;
    }
    static <TT> ArrayList<Integer> filterIndex(java.util.Collection<TT> list,BiFunction<Integer,TT,Boolean> filter) {
        ArrayList<Integer> result = new ArrayList<>();
        int ii = 0;
        for (TT item : list) {
            if (filter.apply(ii++,item))
                result.add(ii);
        }
        return result;
    }
    static enum HandleNulls {
        skip,add,map;
    }

    static <SS,TT> ArrayList<TT> map(java.util.List<SS> src,Function<SS,TT> mapping,HandleNulls nulls) {
        if (nulls==null) nulls = HandleNulls.skip;
        ArrayList<TT> dst = new ArrayList<>();
        for (SS val : src) {
            if (nulls==HandleNulls.map | val != null)
                dst.add(mapping.apply(val));
            else if (val==null & nulls==HandleNulls.add)
                dst.add(null);
        }
        return dst;
    }
    static <SS,TT> ArrayList<TT> mapi(java.util.List<SS> src,BiFunction<SS,Integer,TT> mapping,HandleNulls nulls) {
        if (nulls==null) nulls = HandleNulls.skip;
        ArrayList<TT> dst = new ArrayList<>();
        int ii = 0;
        for (SS val : src) {
            if (nulls==HandleNulls.map | val != null)
                dst.add(mapping.apply(val,ii));
            else if (val==null & nulls==HandleNulls.add)
                dst.add(null);
            ii++;
        }
        return dst;
    }

    public static long timestamp() { return new java.util.Date().getTime(); }
    static<TT> TT either(TT v1,TT v2) { return v1==null ? v2:v1; }
    static<TT> TT either(TT v1,Supplier<TT> v2) { return v1==null ? v2.get():v1; }
    static boolean either(int val,int v1,int v2) { return val==v1 | val==v2; }

    
    
    
    static String decamelify(String text) {
        Matcher mat = Pattern.compile("(?<=[a-z])[A-Z]").matcher(text);
        StringBuffer buf = new StringBuffer();
        while (mat.find())
            mat.appendReplacement(buf, "_"+mat.group());
        mat.appendTail(buf);
        return buf.toString();
    }
    
    public static class FieldCopier<SS,TT> {
        Field[] map, srcFields;
        Class <TT> dstClass;
        BiConsumer<SS,TT> [] extras;
        
        public TT copy(SS src) {
            return copy(src,null);
        }
        public <XX extends TT> XX copy(SS src,XX dst) {
            if (src==null) return dst;
            if (dst==null) dst = (XX) Simple.Reflect.alloc(dstClass,true);
            try {
                for (int ii=0; ii < srcFields.length; ii++)
                    if (map[ii] != null)
                        map[ii].set(dst, srcFields[ii].get(src));
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
            for (BiConsumer extra : extras)
                extra.accept(src,dst);
            return dst;
        }
        public FieldCopier(Class<SS> srcClass,Class<TT> dstClass,BiConsumer<SS,TT> ... extras) {
            this.extras = extras;
            this.dstClass = dstClass;
            srcFields = srcClass.getDeclaredFields();
            Field[] dstFields = dstClass.getDeclaredFields();
            map = new Field[srcFields.length];
            for (int ii=0; ii < srcFields.length; ii++)
                for (int jj=0; jj < dstFields.length; jj++) {
                    Field src = srcFields[ii], dst = dstFields[jj];
                    if (src.getName().equals(dst.getName()) & src.getType().equals(dst.getType())) {
                        src.setAccessible( true );
                        dst.setAccessible( true );
                        map[ii] = dst;
                    }
                }
        }
    }
    
    static public class Box<TT> {
        public TT val;
        public Box() {};
        public Box(TT $val) { val = $val; }
    }
    public static <TT> Box<TT> box() { return new Box(); }
    static public class Ibox {
        public int val;
        public Ibox() {};
        public Ibox(int $val) { val = $val; };
    }
    public static Ibox ibox() { return new Ibox(); }
}
