
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ReactionAddedData {

    @SerializedName("reaction")
    @Expose
    public String reaction;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ReactionAddedData() {
    }

    /**
     * 
     * @param reaction
     */
    public ReactionAddedData(String reaction) {
        super();
        this.reaction = reaction;
    }

}
