
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ReactionRemovedData {

    @SerializedName("reaction")
    @Expose
    public String reaction;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ReactionRemovedData() {
    }

    /**
     * 
     * @param reaction
     */
    public ReactionRemovedData(String reaction) {
        super();
        this.reaction = reaction;
    }

}
