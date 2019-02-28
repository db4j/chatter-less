package foobar;

import static foobar.Utilmm.*;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.rest.FileInfoReps;
import mm.rest.FilesUploadReps;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.Thumbnails.Builder;
import net.coobird.thumbnailator.resizers.DefaultResizerFactory;
import net.coobird.thumbnailator.resizers.Resizer;
import net.coobird.thumbnailator.resizers.ResizerFactory;
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
    
    @PostMapping("/api/v3/users/newimage")
    public Object userUpload(@RequestParam("image") MultipartFile file) {
        try {
            String userid = prep(txn -> {
                String uid = auth(txn);
                Integer kuser = dm.idmap.find(txn,uid);
                dm.users.update(txn,kuser,user -> {
                    user.updateAt = user.lastPictureUpdate = timestamp();
                });
                return uid;
            }).awaitb().val;
            String base = makeFilename(userid);
            File dest = new File(base);
            Thumbnails.of(new ByteArrayInputStream(file.getBytes())).outputFormat("png").size(128,128).toFile(dest);
            return true;
        }
        catch (Exception ex) {}
        return false;
    }

    // fixme - client_ids should be optional
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
        String id = info.id = newid();
        info.name = name;
        String mime = info.mimeType = file.getContentType();
        info.size = file.getSize();

        // fixme::blocking - auth doesn't need to be blocking
        //   however, file upload is inherently blocking
        //   so the right thing is prolly to throttle the number of uploads
        //   maybe use async and a small threadpool
        
        FilesUploadReps reply = new FilesUploadReps();
        reply.clientIds.add(clientIds);
        reply.fileInfos.add(info);

        String base = makeFilename(id);
        File dest = new File(base).getAbsoluteFile();
        File thumb = new File(base+"_thumb");
        File preview = new File(base+"_preview");

        try {
            file.transferTo(dest);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(400).body("saving file failed");
        }
        try {
            Thumbnails.of(dest).size(150,150).outputFormat("jpg").toFile(thumb);
            info.hasPreviewImage = true;
            Shrinker.resize(
                    () -> Thumbnails.of(dest).outputFormat("jpg"),
                    x -> x.size(1024,768),
                    x -> x.toFile(preview),
                    true);
        }
        catch (Exception ex) {}
        
        Integer kfile = prep(txn -> {
            String uid = auth(txn);
            info.userId = uid;
            return dm.addFile(txn,info);
        }).awaitb().val;

        return reply;
    }
    
    public SpringMatter() {}



    
    public static class Shrinker {
        static Factory shrinkFactory = new Factory();
        static DefaultResizerFactory drf = (DefaultResizerFactory) DefaultResizerFactory.getInstance();
        static class Factory implements ResizerFactory {
            public Resizer getResizer() { return null; }
            public Resizer getResizer(Dimension src,Dimension dst) {
                if (src.height < dst.height | src.width < dst.width)
                    throw new BiggerException();
                return drf.getResizer(src,dst);
            }
        }
        static class BiggerException extends RuntimeException {}
        public interface Saver<TT> {
            void exec(TT obj) throws IOException;
        }
        /**
         * render a source image to a size that will never be larger than the source
         * @param <TT> the builder type
         * @param producer the config portion of the builder chain
         * @param sizer the sizing portion of the builder chain, eg size(100,100)
         * @param saver the termination of the builder chain, eg toFile
         * @param always if the source is smaller than the sizer size, scale by 1.0 and save anyway
         * @throws IOException */
        public static <TT> void resize(
                Supplier<Builder<TT>> producer,
                Function<Builder<TT>,Builder<TT>> sizer,
                Saver<Builder<TT>> saver,
                boolean always) throws IOException {
            try {
                saver.exec(sizer.apply(producer.get()).resizerFactory(shrinkFactory));
            }
            catch (BiggerException ex) {
                if (always) saver.exec(producer.get().scale(1.0));
            }
        }
    }
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



