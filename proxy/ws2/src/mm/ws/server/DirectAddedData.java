
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class DirectAddedData {

    @SerializedName("teammate_id")
    @Expose
    public String teammateId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public DirectAddedData() {
    }

    /**
     * 
     * @param teammateId
     */
    public DirectAddedData(String teammateId) {
        super();
        this.teammateId = teammateId;
    }

}
