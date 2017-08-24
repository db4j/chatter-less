
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class GroupAddedData {

    @SerializedName("teammate_ids")
    @Expose
    public String teammateIds;

    /**
     * No args constructor for use in serialization
     * 
     */
    public GroupAddedData() {
    }

    /**
     * 
     * @param teammateIds
     */
    public GroupAddedData(String teammateIds) {
        super();
        this.teammateIds = teammateIds;
    }

}
