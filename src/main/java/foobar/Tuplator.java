package foobar;

import org.db4j.Bhunk;
import org.db4j.Bmeta;
import org.srlutils.Types;
import org.srlutils.btree.Bpage;
import org.srlutils.btree.Btypes;

public class Tuplator {
    
    public static class Pair {
        int v1;
        int v2;
        Pair() {}
        Pair(int $v1,int $v2) { v1=$v1; v2=$v2; }
    }
    
    public static class IIK<TT> extends Bmeta.Toast<Pair,TT,ValsII> {
        { setup(new ValsII(),v2 = new Bhunk.ValsKryo()); }
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
            boolean prefix = (boolean) data;
            int c1 = page.geti(slot,index2), c2 = page.geti(slot+size,index2);
            int delta = (c1==val1.v1 & !prefix) ? val1.v2-c2 : val1.v1-c1;
            return Integer.signum(delta);
        }
        
        public int size() { return size*2; }
    }
    
}
