package foobar;

import foobar.MatterData.Ibox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import mm.rest.UsersStatusIdsRep;
import org.db4j.Bhunk;
import org.db4j.Bmeta;
import org.db4j.Command;
import org.db4j.HunkArray;
import org.srlutils.Types;
import org.srlutils.btree.Bpage;
import org.srlutils.btree.Btypes;

public class Tuplator {
    
    public static class Pair {
        public int v1;
        public int v2;
        public boolean prefix;
        public Pair() {}
        public Pair(int $v1,int $v2) { v1=$v1; v2=$v2; }
        public Pair(int $v1,boolean $prefix) { v1=$v1; prefix=$prefix; }
        public Pair prefix() { prefix=true; return this; }
    }
    
    public static class IIK<TT> extends Bmeta.Toast<Pair,TT,ValsII> {
        { setup(new ValsII(),v2 = new Bhunk.ValsKryo()); }
    }

    
    public static abstract class Base <KK,VV,EE extends Btypes.Element<KK,?>>
            extends Bmeta<Base<KK,VV,EE>.Data,KK,VV,EE> {
        public class Data extends Bmeta.Context<KK,VV,Data> {}
        public Data context() { return new Data(); }
    }    
    
    public static class III extends Base<Pair,Integer,ValsII> {
        { setup(new ValsII(),new Btypes.ValsInt()); }
    }
    // fixme - currently unused, but was tested and works so leaving it - delete if it remains unused
    public static class IPair extends Base<Integer,Pair,Btypes.ValsInt> {
        { setup(new Btypes.ValsInt(),new ValsII()); }
    }
    public static class ValsII extends Btypes.Element<Pair,Boolean> {
        static final int size = Types.Enum._int.size;
        public static class PairData {
        }
        public Boolean compareData(Pair val1,boolean prefix,Object past) {
            return prefix;
        }
        public Pair get(Bpage.Sheet page,int index) {
            return new Pair(page.geti(slot,index),page.geti(slot+size,index));
        }
        public void set(Bpage.Sheet page,int index,Pair val,Object data) {
            page.put(slot,index,val.v1);
            page.put(slot+size,index,val.v2);
        }
        public String format(Pair val) { return String.format("%8d:%8d", val.v1, val.v2); }
        public int compare(Pair val1,Bpage.Sheet page,int index2,Object data) {
            boolean prefix = val1.prefix;
            int c1 = page.geti(slot,index2), c2 = page.geti(slot+size,index2);
            int delta = (c1==val1.v1 & !prefix) ? val1.v2-c2 : val1.v1-c1;
            return Integer.signum(delta);
        }
        
        public int size() { return size*2; }
    }
    
    public static class HunkTuples extends HunkArray<HunkTuples.Tuple,HunkTuples.RwTuple,HunkTuples> {
        public static class Tuple {
            int v1;
            long v2;
            public Tuple(int $v1,long $v2) { v1=$v1; v2=$v2; }
        }
        public static class RwTuple extends Command.RwPrimitive<Tuple,RwTuple> {
            public Tuple val;
            static final int s1 = org.srlutils.Types.Enum._int.size;
            static final int s2 = org.srlutils.Types.Enum._long.size;
            protected void read(Command.Page buf,int offset) {
                prep();
                val.v1 = buf.getInt(offset+0);
                val.v2 = buf.getLong(offset+s1);
            }
            protected void write(Command.Page buf,int offset) {
                buf.putInt(offset+0,val.v1);
                buf.putLong(offset+s1,val.v2);
            }
            public Tuple val() { return val; }
            protected void set2(Tuple value) { val = value; }
            public int size() { return s1+s2; }
            public void set(int v1,long v2) { val = new Tuple(v1,v2); }
            protected void prep() { if (val==null) val = new Tuple(0,0); }
        }
        public RwTuple cmd() { return new RwTuple(); }
    }
    public static enum StatusEnum {
        offline, away, online, unknown;
        static int mask = 0x01 << 30;
        int secret;
        static HunkTuples.Tuple get(UsersStatusIdsRep req) {
            return valueOf(req.status).tuple(req.manual,req.lastActivityAt);
        }
        static UsersStatusIdsRep get(HunkTuples.Tuple tuple) {
            UsersStatusIdsRep rep = new UsersStatusIdsRep();
            rep.lastActivityAt = tuple.v2;
            rep.manual = manual(tuple.v1);
            rep.status = value(tuple.v1);
            return rep;
        }
        static boolean manual(int val) { return (val & mask) != 0; }
        static String value(int val) { return values()[val & ~mask].name(); }
        int key(boolean manual) {
            int key = ordinal();
            assert (key&mask)==0;
            if (manual) key |= mask;
            return key;
        }
        HunkTuples.Tuple tuple(boolean manual,long time) { return new HunkTuples.Tuple(key(manual),time); }
        HunkTuples.RwTuple cmd(boolean manual,long time) { return new HunkTuples.RwTuple().set(tuple(manual,time)); }
    }
    


    public static ArrayList<Integer> join(ArrayList<Integer> ... lists) {
        int num = 0;
        int total = 0;
        int last = -1;
        int first = 0;
        for (int ii=0; ii < lists.length; ii++)
            if (lists[ii] != null) {
                if (num==0) first = ii;
                total += lists[last = ii].size();
                num++;
            }
        if (num==0) return new ArrayList();
        else if (num==1) return lists[last];
        HashMap<Integer,Ibox> map = new HashMap(total);
        ArrayList<Integer> result = new ArrayList<>();
        for (Integer index : lists[first])
            map.put(index,new Ibox(1));
        for (int ii=first+1; ii < last; ii++)
            if (lists[ii] != null)
            for (Integer index : lists[ii]) {
                Ibox box = map.get(index);
                if (box != null) box.val++;
            }
        for (Integer index : lists[last]) {
            Ibox box = map.get(index);
            if (box != null && num==++box.val)
                result.add(index);
        }
        return result;
    }
    public static ArrayList<Integer> not(ArrayList<Integer> list,ArrayList<Integer> not) {
        ArrayList<Integer> result = new ArrayList<>();
        if (list==null) return result;
        if (not==null) return list;
        HashMap<Integer,Integer> map = new HashMap(not.size());
        for (Integer index : not)
            map.put(index,0);
        for (Integer index : list)
            if (map.get(index)==null)
                result.add(index);
        return result;
    }
}
