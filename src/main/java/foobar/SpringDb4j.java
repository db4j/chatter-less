package foobar;

import kilim.Pausable;
import org.db4j.Db4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.async.DeferredResult;

public class SpringDb4j<UU> {
    public Db4j db4j;

    public static class Txn<UU> extends Db4j.Transaction {
        public Txn(Db4j db4j) { set(db4j); }
        public UU auth;
    }
    public static interface Query<TT,UU> {
        public TT query(Txn<UU> txn) throws Pausable;
    }
    public static class DeferredQuery<TT,UU> extends Db4j.Query<DeferredQuery<TT,UU>> {
        Query<TT,UU> body;
        /** the captured return value from the wrapped lambda, valid after query completion */
        public TT val;
        UU auth;
        DeferredResult<ResponseEntity<TT>> result = new DeferredResult<>();
        /**
         * create a new query wrapping body
         * @param body the lambda to delegate to during query task execution
         * @param auth the credentials
         */
        public DeferredQuery(Query<TT,UU> body,UU auth) { this.body = body; this.auth = auth; }
        public void task() throws Pausable {
            val = body.query((Txn<UU>) txn);
            ResponseEntity<TT> resp = ResponseEntity.ok(val);
            result.setResult(resp);
        }

        protected Db4j.Transaction makeTxn(Db4j db4j) {
            Txn self = new Txn(db4j);
            self.auth = auth;
            return self;
        }
    }
    
    public UU getAuth() { return (UU) SecurityContextHolder.getContext().getAuthentication(); }
    
    
    public <TT> DeferredResult<ResponseEntity<TT>> defer(Query<TT,UU> body) {
        DeferredQuery<TT,UU> invoke = new DeferredQuery(body,getAuth());
        db4j.submitQuery(invoke);
        return invoke.result;
    }
    

}
