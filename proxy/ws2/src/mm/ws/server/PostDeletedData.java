
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PostDeletedData {

    @SerializedName("post")
    @Expose
    public String post;

    /**
     * No args constructor for use in serialization
     * 
     */
    public PostDeletedData() {
    }

    /**
     * 
     * @param post
     */
    public PostDeletedData(String post) {
        super();
        this.post = post;
    }

}
