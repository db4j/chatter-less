package foobar;

import static foobar.Utilmm.*;
import java.util.ArrayList;
import mm.data.TeamMembers;
import mm.data.Teams;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
    
@RestController
@ResponseBody
public class SpringMatter extends SpringMatterAuth {

    @RequestMapping("/api/v4/users/me/teams")
    public Object umt() {
        return defer(txn -> {
            String uid = auth(txn);
            ArrayList<Teams> teams = new ArrayList();
            if (uid==null) return null;
            Integer kuser = dm.idmap.find(txn,uid);
            Btree.Range<Btrees.II.Data> range = prefix(txn,dm.temberMap,kuser);
            while (range.next()) {
                TeamMembers tember = dm.tembers.find(txn,range.cc.val);
                Integer kteam = dm.idmap.find(txn,tember.teamId);
                teams.add(dm.teams.find(txn,kteam));
            }
            return map(teams,team -> team2reps.copy(team),Utilmm.HandleNulls.skip);
        });
    }
    
    
    public SpringMatter() {}

}




//  curl="curl 'http://127.0.0.2:8080/api/v4/users/me/teams' -H 'Cookie: ajs_user_id=null; ajs_group_id=null; ajs_anonymous_id=%2200000000000000000000000000%22; MMUSERID=npsevevqwtnc8pdn1t1p20eogv; MMAUTHTOKEN=ltytkx1spwf3bfb1ukdc8amgc4' -H 'Accept-Encoding: gzip, deflate, br' -H 'Accept-Language: en-US,en;q=0.9,es-MX;q=0.8,es;q=0.7' -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36' -H 'Accept: */*' -H 'X-Requested-With: XMLHttpRequest' -H 'Connection: keep-alive' --compressed"
// cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
// jdebug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000"
// mvn package && java $jdebug -cp $cp:target/classes foobar.MatterFull > /dev/null &



