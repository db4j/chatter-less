
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PostEditedData {

    @SerializedName("post")
    @Expose
    public String post;

    /**
     * No args constructor for use in serialization
     * 
     */
    public PostEditedData() {
    }

    /**
     * 
     * @param post
     */
    public PostEditedData(String post) {
        super();
        this.post = post;
    }

}
