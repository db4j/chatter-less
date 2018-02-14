package foobar;

import static foobar.Utilmm.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.rest.FileInfoReps;
import mm.rest.FilesUploadReps;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
    
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
    
    @PostMapping("/api/v3/teams/{teamid}/files/upload")
    public Object handleFileUpload(
            @PathVariable String teamid,
            @RequestParam("channel_id") String chanid,
            @RequestParam("client_ids") String clientIds,
            @RequestParam("files") MultipartFile file
    ) {
        String name = file.getOriginalFilename();

        FileInfoReps info = new FileInfoReps();
        info.createAt = info.updateAt = timestamp();
        int dot = name.lastIndexOf(".");
        info.extension = dot < 0 ? "" : name.substring(dot+1);
        info.hasPreviewImage = false;
        String id = info.id = newid();
        info.name = name;
        info.mimeType = file.getContentType();
        info.size = file.getSize();

        // fixme::blocking - the file portion prolly needs to be blocking, but the auth doesn't
        //   it's easy to do but looks ugly
        
        Integer kfile = prep(txn -> {
            String uid = auth(txn);
            info.userId = uid;
            return dm.addFile(txn,info);
        }).awaitb().val;

        FilesUploadReps reply = new FilesUploadReps();
        reply.clientIds.add(clientIds);
        reply.fileInfos.add(info);

        String base = "db_files/files/" + id;
        File tmp = new File(id);
        File tmpd = new File(base+".tmp").getAbsoluteFile();
        File dest = new File(base);

        try {
            file.transferTo(tmp);
            file.transferTo(tmpd);
            Files.move(tmpd.toPath(),dest.toPath());
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(400).body("saving file failed");
        }
        
        return reply;
    }
    
    public SpringMatter() {}

}

// spring file upload with nonblocking io
//   https://github.com/hantsy/spring-reactive-sample/tree/master/multipart
// another post, just a snippet:
//   https://stackoverflow.com/questions/47703924/spring-web-reactive-framework-multipart-file-issue
// what to google for:
//   spring reactive file upload 

// ideas for other demos:
//   https://github.com/orlandovald/webflux-twitter-demo
//   this uses mongodb with a reactive driver - seems like db4j integration would be viable


//  curl="curl 'http://127.0.0.2:8080/api/v4/users/me/teams' -H 'Cookie: ajs_user_id=null; ajs_group_id=null; ajs_anonymous_id=%2200000000000000000000000000%22; MMUSERID=npsevevqwtnc8pdn1t1p20eogv; MMAUTHTOKEN=ltytkx1spwf3bfb1ukdc8amgc4' -H 'Accept-Encoding: gzip, deflate, br' -H 'Accept-Language: en-US,en;q=0.9,es-MX;q=0.8,es;q=0.7' -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36' -H 'Accept: */*' -H 'X-Requested-With: XMLHttpRequest' -H 'Connection: keep-alive' --compressed"
// cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
// jdebug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000"
// mvn package && java $jdebug -cp $cp:target/classes foobar.MatterFull > /dev/null &



