
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class EphemeralMessageData {

    @SerializedName("post")
    @Expose
    public String post;

    /**
     * No args constructor for use in serialization
     * 
     */
    public EphemeralMessageData() {
    }

    /**
     * 
     * @param post
     */
    public EphemeralMessageData(String post) {
        super();
        this.post = post;
    }

}
